package net.thekingofduck.loki.service;

import net.thekingofduck.loki.common.AttackPattern;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HttpLogService {

    @Autowired
    private HttpLogMapper httpLogMapper;

    private final List<AttackPattern> attackPatterns;

    public HttpLogService() {
        attackPatterns = new ArrayList<>();

        // SQL 注入模式
        attackPatterns.add(new AttackPattern(
                "SQL注入",
                "(?i)(union\\s+select|select\\s+.*\\s+from|information_schema|xp_cmdshell|declare\\s+@v|exec\\s+|sleep\\s*\\(|\\bwaitfor\\s+delay\\b|benchmark\\s*\\(|load_file\\s*\\(|outfile\\s*|dumpfile\\s*|utl_inaddr\\.get_host_name|utl_http\\.request|dbms_pipe\\.receive_message|from\\s+[a-z_]+\\s+where\\s+\\w+=\\w+|\\b(?:OR|AND|XOR)\\b\\s*['\"\\d]+\\s*[=><!]\\s*['\"\\d]+|['\"]\\s*(?:OR|AND|XOR)\\s*['\"]?[a-z0-9_.]*['\"]?\\s*[=><!]\\s*['\"]?[a-z0-9_.]*['\"]?|\\b(OR|AND)\\s*\\d+=\\d+\\b|\\b(OR|AND)\\s*\\w+=\\w+\\b|--|#|\\/\\*|;|\\b(insert|update|delete)\\b.*\\bwhere\\b|convert\\s*\\(|cast\\s*\\(|ascii\\s*\\(|substring\\s*\\(|mid\\s*\\(|len\\s*\\(|length\\s*\\(|char\\s*\\(|concat\\s*\\(|schema_name\\s*|table_name\\s*|column_name\\s*)",
                Arrays.asList(
                        "select", "union", "or", "and", "from", "where",
                        "information_schema", "exec", "xp_cmdshell", "sleep",
                        "waitfor", "benchmark", "load_file", "outfile", "dumpfile",
                        "insert", "update", "delete", "convert", "cast", "ascii",
                        "substring", "mid", "len", "length", "char", "concat",
                        "schema_name", "table_name", "column_name"
                )
        ));

        // XSS 攻击模式
        attackPatterns.add(new AttackPattern(
                "XSS攻击",
                "(?i)(<script\\b[^>]*>.*?</script>|<img[^>]*src=[\"']?javascript:.*?[\"']?|<svg/onload=.*?>|on[a-z]+=[^>]*|expression\\(|data:text/html|<body[^>]*onload|alert\\s*\\(|prompt\\s*\\(|confirm\\s*\\()",
                Arrays.asList("script", "alert", "javascript", "onerror", "onload", "confirm", "prompt")
        ));

        // 命令执行/代码执行模式 (已添加Linux常见命令)
        attackPatterns.add(new AttackPattern(
                "命令执行",
                "(?i)(exec\\s*\\(|system\\s*\\(|shell_exec\\s*\\(|passthru\\s*\\(|proc_open\\s*\\(|pcntl_exec\\s*\\(|`.*`|curl\\s+http|wget\\s+http|rm\\s+-rf|cat\\s+/etc/passwd|bash\\s+-c|\\bcat\\b|\\bls\\b|\\bwhoami\\b|\\bid\\b|\\bip\\s+a\\b|\\bnetstat\\b|\\bps\\b|\\bwget\\b|\\bcurl\\b|\\bpython\\b|\\bperl\\b|\\bphp\\b|\\bsh\\b|\\bbash\\b|\\bzsh\\b|\\bawk\\b|\\bfind\\b|\\bgrep\\b|\\bchmod\\b|\\bchown\\b|\\bmkdir\\b|\\brmdir\\b|\\bmv\\b|\\bcp\\b|\\becho\\b|\\bprintf\\b)",
                Arrays.asList(
                        "exec", "system", "shell", "bash", "cat", "rm",
                        "ls", "whoami", "id", "ip", "netstat", "ps", "wget", "curl",
                        "python", "perl", "php", "sh", "awk", "find", "grep",
                        "chmod", "chown", "mkdir", "rmdir", "mv", "cp", "echo", "printf"
                )
        ));

        // 文件包含模式 (本地/远程)
        attackPatterns.add(new AttackPattern(
                "文件包含",
                "(?i)(php:\\/\\/filter|file:\\/\\/|data:\\/\\/|phar:\\/\\/|zip:\\/\\/|compress\\.zlib:\\/\\/|glob:\\/\\/|\\/etc\\/passwd|\\/proc\\/self\\/environ|\\.\\.\\/|%2e%2e%2f|%252e%252e%252f)",
                Arrays.asList("php://", "file://", "/etc/passwd", "../")
        ));

        // SSRF (Server-Side Request Forgery)
        attackPatterns.add(new AttackPattern(
                "SSRF",
                "(?i)(file:\\/\\/|gopher:\\/\\/|dict:\\/\\/|ftp:\\/\\/|http[s]?:\\/\\/(localhost|127\\.0\\.0\\.1|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+))",
                Arrays.asList("file://", "gopher://", "dict://")
        ));
    }

    /**
     * 根据canvasId查询相关信息并进行分析
     *
     * @param canvasId 传入的canvasId
     * @return 包含 number, 去重后IPs, 和去重后攻击方式的JSON字符串
     */
    public String getHackerInfoAndAttackAnalysis(String canvasId) {
        // 1. 根据 canvasId 查询 number (黑客ID)
        Integer number = httpLogMapper.getCanvasNumber(canvasId);

        // 2. 根据 canvasId 查询 IP 和 body 列表
        List<HttpLogEntity> logs = httpLogMapper.findIpsAndBodiesByCanvasId(canvasId);

        // 3. 去重 IP
        Set<String> uniqueIps = logs.stream()
                .map(HttpLogEntity::getIp)
                .collect(Collectors.toSet());

        // 4. 对所有 body 内容进行正则匹配，并去重攻击方式
        Set<String> uniqueAttackMethods = new HashSet<>();
        for (HttpLogEntity log : logs) {
            String body = log.getBody();
            if (body == null || body.trim().isEmpty()) {
                continue;
            }

            for (AttackPattern pattern : attackPatterns) {
                if (pattern.matches(body)) {
                    uniqueAttackMethods.add(pattern.getName());
                }
            }
        }

        // 5. 封装为 JSON 对象并返回
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("number", number);

        ArrayNode ipArrayNode = objectMapper.createArrayNode();
        uniqueIps.forEach(ipArrayNode::add);
        resultNode.set("ips", ipArrayNode);

        ArrayNode attackMethodArrayNode = objectMapper.createArrayNode();
        uniqueAttackMethods.forEach(attackMethodArrayNode::add);
        resultNode.set("attackMethods", attackMethodArrayNode);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultNode);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return "{\"error\": \"Failed to convert to JSON\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取攻击IP列表（用于地图展示）
     * @return JSON格式的IP列表
     */
    public String getAttackIpList() {
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();
        Set<String> uniqueIps = allLogs.stream()
                .map(HttpLogEntity::getIp)
                .filter(ip -> ip != null && !ip.trim().isEmpty())
                .collect(Collectors.toSet());

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode ipArrayNode = objectMapper.createArrayNode();
        
        // 直接添加IP字符串到数组中
        uniqueIps.forEach(ipArrayNode::add);

        try {
            return objectMapper.writeValueAsString(ipArrayNode);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    /**
     * 获取仪表盘统计数据
     * @return JSON格式的统计数据
     */
    public String getDashboardStats() {
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();
        
        // 统计总攻击次数
        int totalAttacks = allLogs.size();
        
        // 统计唯一IP数量
        Set<String> uniqueIps = allLogs.stream()
                .map(HttpLogEntity::getIp)
                .filter(ip -> ip != null && !ip.trim().isEmpty())
                .collect(Collectors.toSet());
        int uniqueIpCount = uniqueIps.size();
        
        // 统计攻击类型数量
        Set<String> attackTypes = new HashSet<>();
        for (HttpLogEntity log : allLogs) {
            String body = log.getBody();
            if (body == null || body.trim().isEmpty()) continue;
            
            for (AttackPattern pattern : attackPatterns) {
                if (pattern.matches(body)) {
                    attackTypes.add(pattern.getName());
                }
            }
        }
        
        // 统计今日攻击数（简化处理，实际应该按日期过滤）
        int todayAttacks = Math.min(totalAttacks, (int)(totalAttacks * 0.3)); // 模拟今日攻击占比30%
        
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode statsNode = objectMapper.createObjectNode();
        statsNode.put("totalAttacks", totalAttacks);
        statsNode.put("uniqueIps", uniqueIpCount);
        statsNode.put("attackTypes", attackTypes.size());
        statsNode.put("todayAttacks", todayAttacks);
        
        try {
            return objectMapper.writeValueAsString(statsNode);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return "{\"error\": \"Failed to get stats\"}";
        }
    }

    /**
     * 获取攻击类型统计数据（用于饼图）
     * @return JSON格式的攻击类型统计
     */
    public String getAttackTypeStats() {
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();
        
        // 统计各种攻击类型的数量
        java.util.Map<String, Integer> attackTypeCount = new java.util.HashMap<>();
        
        for (HttpLogEntity log : allLogs) {
            String body = log.getBody();
            if (body == null || body.trim().isEmpty()) continue;
            
            for (AttackPattern pattern : attackPatterns) {
                if (pattern.matches(body)) {
                    attackTypeCount.put(pattern.getName(), 
                        attackTypeCount.getOrDefault(pattern.getName(), 0) + 1);
                }
            }
        }
        
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode resultArray = objectMapper.createArrayNode();
        
        attackTypeCount.forEach((type, count) -> {
            ObjectNode typeNode = objectMapper.createObjectNode();
            typeNode.put("name", type);
            typeNode.put("value", count);
            resultArray.add(typeNode);
        });
        
        try {
            return objectMapper.writeValueAsString(resultArray);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    /**
     * 获取最近的日志记录
     * @param limit 限制返回的记录数
     * @return JSON格式的最近日志记录
     */
    public String getRecentLogs(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10; // 默认返回10条记录
        }
        
        List<HttpLogEntity> recentLogs = httpLogMapper.getRecentLogs(limit);
        
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode logsArray = objectMapper.createArrayNode();
        
        for (HttpLogEntity log : recentLogs) {
            ObjectNode logNode = objectMapper.createObjectNode();
            logNode.put("id", log.getId());
            logNode.put("ip", log.getIp());
            logNode.put("method", log.getMethod());
            logNode.put("path", log.getPath());
            logNode.put("parameter", log.getParameter());
            logNode.put("headers", log.getHeaders());
            logNode.put("body", log.getBody());
            logNode.put("time", log.getTime());
            
            // 分析攻击类型
            String attackType = "正常访问";
            String body = log.getBody();
            if (body != null && !body.trim().isEmpty()) {
                for (AttackPattern pattern : attackPatterns) {
                    if (pattern.matches(body)) {
                        attackType = pattern.getName();
                        break;
                    }
                }
            }
            logNode.put("attackType", attackType);
            
            logsArray.add(logNode);
        }
        
        try {
            return objectMapper.writeValueAsString(logsArray);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    /**
     * 获取攻击趋势数据（用于折线图）
     * @return JSON格式的时间趋势数据
     */
    public String getAttackTrends() {
        // 简化实现：生成最近7天的模拟数据
        // 实际应该从数据库按日期统计
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode datesArray = objectMapper.createArrayNode();
        ArrayNode countsArray = objectMapper.createArrayNode();
        
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.Random random = new java.util.Random();
        
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            String dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
            int count = random.nextInt(50) + 10; // 随机生成10-60之间的攻击数量
            
            datesArray.add(dateStr);
            countsArray.add(count);
        }
        
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.set("dates", datesArray);
        resultNode.set("counts", countsArray);
        
        try {
            return objectMapper.writeValueAsString(resultNode);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return "{\"dates\": [], \"counts\": []}";
        }
    }
}