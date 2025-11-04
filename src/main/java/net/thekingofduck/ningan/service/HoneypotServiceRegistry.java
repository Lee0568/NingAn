package net.thekingofduck.ningan.service;

import net.thekingofduck.ningan.entity.HoneypotService;
import net.thekingofduck.ningan.core.TomcatPortManager;
import net.thekingofduck.ningan.entity.TemplateEntity;
import net.thekingofduck.ningan.protocol.ssh.SshHoneypotServer;
import net.thekingofduck.ningan.protocol.sql.SqlHoneypotServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单的内存注册表：管理蜜罐服务列表与状态
 * 初始化时从 TemplateEntity 读取模板配置，生成默认服务列表
 */
@Service
public class HoneypotServiceRegistry {

    private final Map<Long, HoneypotService> services = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @Autowired
    private TemplateEntity templates;

    @Value("${server.port}")
    private int serverPort;

    @Value("${server.multiPorts}")
    private String multiPorts; // 逗号分隔，支持区间

    // 启动时的静态端口集合（server.port + multiPorts）
    private Set<Integer> staticPorts = new HashSet<>();
    // 当前在线端口集合（包含静态和运行时动态添加）
    private Set<Integer> activePorts = new HashSet<>();

    // 协议级服务实例（SSH / SQL）
    private final Map<Long, SshHoneypotServer> sshServers = new ConcurrentHashMap<>();
    private final Map<Long, SqlHoneypotServer> sqlServers = new ConcurrentHashMap<>();

    @Autowired
    private TomcatPortManager portManager;

    @Autowired
    private SshAuditService sshAuditService;

    @Autowired
    private SqlAuditService sqlAuditService;

    @PostConstruct
    public void init() {
        // 计算静态端口集合
        staticPorts = parseActivePorts(multiPorts);
        staticPorts.add(serverPort);
        // 初始化当前在线端口集合
        activePorts = new HashSet<>(staticPorts);

        // 从配置模板初始化服务
        Map<String, List<Map<String, Object>>> list = templates.getList();
        if (list != null) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : list.entrySet()) {
                String templateName = entry.getKey();
                List<Map<String, Object>> items = entry.getValue();
                if (items == null || items.isEmpty()) continue;
                Object mapsObj = items.get(0).get("maps");
                if (!(mapsObj instanceof Map)) continue;
                Map<?, ?> maps = (Map<?, ?>) mapsObj;

                Integer port = tryParseInt(String.valueOf(maps.get("port")), null);
                String path = String.valueOf(maps.get("path"));

                HoneypotService svc = new HoneypotService();
                svc.setId(idGen.getAndIncrement());
                svc.setPort(port != null ? port : serverPort);
                svc.setTemplate(templateName);
                svc.setType(guessTypeByTemplate(templateName));
                svc.setName(templateName + " 服务");
                svc.setDesc("模板: " + path);
                boolean running = port != null && activePorts.contains(port);
                svc.setStatus(running ? "running" : "stopped");
                svc.setUptime(running ? "已运行" : "未启动");
                svc.setRequests(0);

                services.put(svc.getId(), svc);
            }
        }
    }

    public List<HoneypotService> list() {
        return new ArrayList<>(services.values());
    }

    public HoneypotService add(HoneypotService svc) {
        svc.setId(idGen.getAndIncrement());
        if (svc.getTemplate() == null) svc.setTemplate("default");
        // 始终根据模板判定服务类型，避免前端传入的 type 干扰判断
        svc.setType(guessTypeByTemplate(svc.getTemplate()));
        if (svc.getStatus() == null) svc.setStatus("running");
        if (svc.getUptime() == null) svc.setUptime("刚刚启动");
        if (svc.getRequests() == null) svc.setRequests(0);

        services.put(svc.getId(), svc);

        Integer port = svc.getPort();
        // 根据类型分别处理
        if ("SSH服务".equals(svc.getType())) {
            // 启动协议级SSH蜜罐
            if (port != null) {
                SshHoneypotServer server = new SshHoneypotServer(port);
                server.setAuditService(sshAuditService);
                try {
                    server.start();
                    sshServers.put(svc.getId(), server);
                } catch (Exception e) {
                    // 启动失败，标记为停止
                    svc.setStatus("stopped");
                    svc.setUptime("--");
                }
            }
        } else if ("SQL服务".equals(svc.getType())) {
            // 启动协议级SQL蜜罐
            if (port != null) {
                SqlHoneypotServer server = new SqlHoneypotServer(port);
                server.setAuditService(sqlAuditService);
                try {
                    server.start();
                    sqlServers.put(svc.getId(), server);
                } catch (Exception e) {
                    svc.setStatus("stopped");
                    svc.setUptime("--");
                }
            }
        } else {
            // Web类型：端口尚未在线则在部署时立即开启HTTP端口
            if (port != null && !activePorts.contains(port)) {
                boolean ok = portManager.addPort(port, false);
                if (ok) activePorts.add(port);
            }
        }
        return svc;
    }

    public Optional<HoneypotService> get(Long id) {
        return Optional.ofNullable(services.get(id));
    }

    public boolean delete(Long id) {
        return services.remove(id) != null;
    }

    public boolean start(Long id) {
        HoneypotService svc = services.get(id);
        if (svc == null) return false;
        svc.setStatus("running");
        svc.setUptime("刚刚启动");
        Integer port = svc.getPort();
        String inferred = guessTypeByTemplate(svc.getTemplate());
        if ("SSH服务".equals(inferred)) {
            // 启动或重启SSH服务
            SshHoneypotServer server = sshServers.get(id);
            if (server == null && port != null) {
                server = new SshHoneypotServer(port);
                server.setAuditService(sshAuditService);
                sshServers.put(id, server);
            }
            try {
                if (server != null && !server.isRunning()) server.start();
            } catch (Exception e) {
                return false;
            }
        } else if ("SQL服务".equals(inferred)) {
            // 启动或重启SQL服务
            SqlHoneypotServer server = sqlServers.get(id);
            if (server == null && port != null) {
                server = new SqlHoneypotServer(port);
                server.setAuditService(sqlAuditService);
                sqlServers.put(id, server);
            }
            try {
                if (server != null && !server.isRunning()) server.start();
            } catch (Exception e) {
                return false;
            }
        } else {
            // Web 类型：动态开启HTTP端口
            if (port != null && !activePorts.contains(port)) {
                boolean ok = portManager.addPort(port, false);
                if (ok) activePorts.add(port);
            }
        }
        return true;
    }

    public boolean stop(Long id) {
        HoneypotService svc = services.get(id);
        if (svc == null) return false;
        svc.setStatus("stopped");
        svc.setUptime("--");
        Integer port = svc.getPort();
        String inferred = guessTypeByTemplate(svc.getTemplate());
        if ("SSH服务".equals(inferred)) {
            SshHoneypotServer server = sshServers.get(id);
            if (server != null && server.isRunning()) server.stop();
        } else if ("SQL服务".equals(inferred)) {
            SqlHoneypotServer server = sqlServers.get(id);
            if (server != null && server.isRunning()) server.stop();
        } else {
            // 对运行时添加的HTTP端口才移除；静态端口保留
            if (port != null && activePorts.contains(port) && !staticPorts.contains(port)) {
                boolean ok = portManager.removePort(port);
                if (ok) activePorts.remove(port);
            }
        }
        return true;
    }

    public HoneypotService update(Long id, Map<String, Object> changes) {
        HoneypotService svc = services.get(id);
        if (svc == null) return null;
        if (changes.containsKey("port")) svc.setPort(tryParseInt(String.valueOf(changes.get("port")), svc.getPort()));
        if (changes.containsKey("type")) svc.setType(String.valueOf(changes.get("type")));
        if (changes.containsKey("template")) svc.setTemplate(String.valueOf(changes.get("template")));
        if (changes.containsKey("name")) svc.setName(String.valueOf(changes.get("name")));
        if (changes.containsKey("desc")) svc.setDesc(String.valueOf(changes.get("desc")));
        return svc;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> m = new HashMap<>();
        m.put("cpu", String.format(Locale.ROOT, "%.1f", (Math.random() * 30 + 10)));
        m.put("memory", String.format(Locale.ROOT, "%.1f", (Math.random() * 40 + 30)));
        m.put("connections", (int) (Math.random() * 50 + 20));
        m.put("attacks", (int) (Math.random() * 10 + 5));
        return m;
    }

    private Set<Integer> parseActivePorts(String multiPortsStr) {
        Set<Integer> set = new HashSet<>();
        if (multiPortsStr == null || multiPortsStr.isEmpty()) return set;
        String[] parts = multiPortsStr.split(",");
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (p.contains("-")) {
                String[] range = p.split("-");
                Integer start = tryParseInt(range[0], null);
                Integer end = tryParseInt(range[1], null);
                if (start != null && end != null) {
                    for (int i = start; i <= end; i++) set.add(i);
                }
            } else {
                Integer val = tryParseInt(p, null);
                if (val != null) set.add(val);
            }
        }
        return set;
    }

    private Integer tryParseInt(String s, Integer defVal) {
        try { return Integer.parseInt(s); } catch (Exception e) { return defVal; }
    }

    private String guessTypeByTemplate(String template) {
        if (template == null) return "Web服务";
        String t = template.toLowerCase(Locale.ROOT);
        if (t.contains("ssh")) return "SSH服务";
        if (t.contains("sql")) return "SQL服务";
        if (t.contains("ftp")) return "FTP服务";
        if (t.contains("telnet")) return "Telnet服务";
        return "Web服务";
    }
}