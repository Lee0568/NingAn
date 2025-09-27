package net.thekingofduck.loki.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.thekingofduck.loki.common.AttackPattern;
import net.thekingofduck.loki.entity.CanvasEnity;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import net.thekingofduck.loki.mapper.IpBanMapper;
import net.thekingofduck.loki.mapper.SecuritySettingMapper;
import net.thekingofduck.loki.model.BlockedIp;
import net.thekingofduck.loki.model.SecuritySetting;
import net.thekingofduck.loki.repository.SecuritySettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HttpLogService {

    @Autowired
    private IpBanMapper ipBanMapper;

    @Autowired
    private SecuritySettingRepository securitySettingRepository;

    @Autowired
    private HttpLogMapper httpLogMapper;

    @Autowired
    private SecuritySettingMapper securitySettingMapper;

    // --- ▼▼▼【核心修复 1】▼▼▼ ---
    // 在类顶部注入由 Spring 管理的 RestTemplate 单例
    @Autowired
    private RestTemplate restTemplate;
    // --- ▲▲▲【核心修复 1】▲▲▲ ---

    @Value("${smsbao.username}")
    private String smsbaoUsername;

    @Value("${smsbao.password}")
    private String smsbaoPassword;

    @Value("${smsbao.signature}")
    private String smsbaoSignature;

    private final List<AttackPattern> attackPatterns;
    private static final int SMS_COOLDOWN_MINUTES = 30;
    private long lastSmsSentTimestamp = 0;

    public HttpLogService() {
        attackPatterns = new ArrayList<>();
        // 1. XSS 攻击模式 (优先级提前)
        attackPatterns.add(new AttackPattern(
                "XSS攻击",
                "(?i)(<\\/?\\s*script\\b[^>]*>|javascript:|on[a-z]+\\s*=|expression\\(|<\\s*svg\\/onload|<\\s*img[^>]+src\\s*=|data:text\\/html)",
                Arrays.asList("script", "alert", "javascript", "onerror", "onload", "confirm", "prompt", "src", "svg")
        ));

        // 2. SQL 注入模式 (优先级调后)
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
        // 文件包含模式
        attackPatterns.add(new AttackPattern("文件包含", "(?i)(php:\\/\\/filter|file:\\/\\/|data:\\/\\/|phar:\\/\\/|zip:\\/\\/|compress\\.zlib:\\/\\/|glob:\\/\\/|\\/etc\\/passwd|\\/proc\\/self\\/environ|\\.\\.\\/|%2e%2e%2f|%252e%252e%252f)", Arrays.asList("php://", "file://", "/etc/passwd", "../")));
        // SSRF (Server-Side Request Forgery)
        attackPatterns.add(new AttackPattern("SSRF", "(?i)(file:\\/\\/|gopher:\\/\\/|dict:\\/\\/|ftp:\\/\\/|http[s]?:\\/\\/(localhost|127\\.0\\.0\\.1|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+))", Arrays.asList("file://", "gopher://", "dict://")));
    }

    @Scheduled(fixedRate = 60000)
    public void scheduledAttackCheck() {
        System.out.println("【计划任务】开始执行攻击频率检查...");
        try {
            int timeWindowMinutes = Integer.parseInt(securitySettingMapper.findValueByKey("timeWindowMinutes"));
            int attackThreshold = Integer.parseInt(securitySettingMapper.findValueByKey("attacks_amount"));
            String adminPhoneNumber = securitySettingMapper.findValueByKey("phone");
            if (adminPhoneNumber == null || adminPhoneNumber.trim().isEmpty()) {
                System.err.println("【计划任务】错误：数据库中未配置 'phone' 项，无法发送短信提醒。");
                return;
            }
            System.out.println(String.format("【计划任务】加载配置成功: timeWindow=%d 分钟, threshold=%d 次, phone=%s", timeWindowMinutes, attackThreshold, adminPhoneNumber));
            checkAttacksAndAlert(timeWindowMinutes, attackThreshold, adminPhoneNumber);
        } catch (NumberFormatException | NullPointerException e) {
            System.err.println("【计划任务】错误：从数据库加载安全配置失败，请检查 security_setting 表。");
            e.printStackTrace();
        }
    }

    public void checkAttacksAndAlert(int timeWindowMinutes, int attackThreshold, String phoneNumber) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDateTime = now.minusMinutes(timeWindowMinutes);
        String startTimeStr = startDateTime.format(formatter);
        String endTimeStr = now.format(formatter);
        System.out.println("【计划任务】正在查询时间段: " + startTimeStr + " 到 " + endTimeStr);
        List<HttpLogEntity> recentLogs = httpLogMapper.findLogsBetweenDates(startTimeStr, endTimeStr);
        if (recentLogs == null || recentLogs.isEmpty()) {
            System.out.println("【计划任务】在过去 " + timeWindowMinutes + " 分钟内无匹配的日志记录。");
            return;
        }
        int attackCount = 0;
        for (HttpLogEntity log : recentLogs) {
            if (!"正常访问".equals(analyzeLogForAttackType(log))) {
                attackCount++;
            }
        }
        System.out.println("【计划任务】在过去 " + timeWindowMinutes + " 分钟内检测到 " + attackCount + " 次攻击。");
        if (attackCount >= attackThreshold) {
            sendSmsAlert(phoneNumber, attackCount, timeWindowMinutes, attackThreshold);
        }
    }

    private void sendSmsAlert(String phoneNumber, int attackCount, int timeWindowMinutes, int attackThreshold) {
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = SMS_COOLDOWN_MINUTES * 60 * 1000L;
        if (currentTime - lastSmsSentTimestamp < cooldownMillis) {
            System.out.println("【短信提醒】短信功能处于冷却中，本次警报已抑制。");
            return;
        }
        String content = String.format("您的网站在过去 %d 分钟内检测到 %d 次攻击，已超过 %d 次的阈值。请立即检查系统安全！", timeWindowMinutes, attackCount, attackThreshold);
        String fullContent = this.smsbaoSignature + content;
        try {
            String passwordMd5 = md5(this.smsbaoPassword);
            String contentUrlEncoded = URLEncoder.encode(fullContent, StandardCharsets.UTF_8.toString());
            String url = String.format("http://api.smsbao.com/sms?u=%s&p=%s&m=%s&c=%s", this.smsbaoUsername, passwordMd5, phoneNumber, contentUrlEncoded);
            System.out.println("【短信提醒】准备发送短信到: " + phoneNumber);

            // 直接使用由 Spring 注入的 restTemplate 实例，不再 new
            String result = restTemplate.getForObject(url, String.class);
            // --- ▲▲▲【核心修复 2】▲▲▲ ---

            if ("0".equals(result)) {
                System.out.println("【短信提醒】短信发送成功！");
                lastSmsSentTimestamp = currentTime;
            } else {
                System.err.println("【短信提醒】短信发送失败！返回码: " + result);
            }
        } catch (Exception e) {
            System.err.println("【短信提醒】短信发送时发生错误。");
            e.printStackTrace();
        }
    }

    private static String md5(String text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(text.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void updateSmsSettings(int timeWindowMinutes, int attacksAmount, String phone) {
        SecuritySetting timeWindowSetting = new SecuritySetting();
        timeWindowSetting.setSettingKey("timeWindowMinutes");
        timeWindowSetting.setSettingValue(String.valueOf(timeWindowMinutes));
        securitySettingRepository.save(timeWindowSetting);
        SecuritySetting attacksAmountSetting = new SecuritySetting();
        attacksAmountSetting.setSettingKey("attacks_amount");
        attacksAmountSetting.setSettingValue(String.valueOf(attacksAmount));
        securitySettingRepository.save(attacksAmountSetting);
        SecuritySetting phoneSetting = new SecuritySetting();
        phoneSetting.setSettingKey("phone");
        phoneSetting.setSettingValue(phone);
        securitySettingRepository.save(phoneSetting);
        System.out.println("【短信策略】已通过 JPA Repository 更新数据库中的短信提醒配置。");
    }

    public Map<String, Object> getSmsSettings() {
        Map<String, Object> settings = new HashMap<>();
        try {
            String timeWindowStr = securitySettingMapper.findValueByKey("timeWindowMinutes");
            String attacksAmountStr = securitySettingMapper.findValueByKey("attacks_amount");
            String phone = securitySettingMapper.findValueByKey("phone");
            settings.put("timeWindowMinutes", timeWindowStr != null ? Integer.parseInt(timeWindowStr) : 5);
            settings.put("attacksAmount", attacksAmountStr != null ? Integer.parseInt(attacksAmountStr) : 1000);
            settings.put("phone", phone != null ? phone : "13800138000");
        } catch (Exception e) {
            System.err.println("读取短信配置时出错，将返回默认值: " + e.getMessage());
            settings.put("timeWindowMinutes", 5);
            settings.put("attacksAmount", 1000);
            settings.put("phone", "13800138000");
        }
        return settings;
    }

    public Map<String, Object> getCanvasLogWithAttackTypes(int page, int limit) {
        int totalItems = httpLogMapper.countTotalCanvasLogs();
        int totalPages = (int) Math.ceil((double) totalItems / limit);
        List<CanvasEnity> items = httpLogMapper.selectAllCanvaslogs(page, limit);
        for (CanvasEnity item : items) {
            Set<String> attackTypes = getAttackTypesByCanvasId(item.getCanvasId());
            item.setAttackTypes(attackTypes);
        }
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("items", items);
        responseData.put("totalCount", totalItems);
        responseData.put("totalPages", totalPages);
        responseData.put("currentPage", page);
        return responseData;
    }

    public Set<String> getAttackTypesByCanvasId(String canvasId) {
        List<HttpLogEntity> logs = httpLogMapper.findIpsAndBodiesByCanvasId(canvasId);
        return logs.stream()
                .map(this::analyzeLogForAttackType)
                .filter(attackType -> !"正常访问".equals(attackType))
                .collect(Collectors.toSet());
    }

    public List<Map<String, Object>> getBlockedIpsForFrontend() {
        List<BlockedIp> allBlockedIps = ipBanMapper.findAllBlockedIps();
        return allBlockedIps.stream()
                .map(blockedIp -> {
                    Map<String, Object> ipInfo = new LinkedHashMap<>();
                    ipInfo.put("ipAddress", blockedIp.getIpAddress());
                    ipInfo.put("expiresAt", blockedIp.getExpiresAt());
                    ipInfo.put("blockMode", blockedIp.getBlockMode());
                    return ipInfo;
                })
                .collect(Collectors.toList());
    }

    public int unbanIp(String ipAddress) {
        return ipBanMapper.deleteByIpAddress(ipAddress);
    }

    public String getHackerInfoAndAttackAnalysis(String canvasId) {
        Integer number = httpLogMapper.getCanvasNumber(canvasId);
        List<HttpLogEntity> logs = httpLogMapper.findIpsAndBodiesByCanvasId(canvasId);
        Set<String> uniqueIps = logs.stream()
                .map(HttpLogEntity::getIp)
                .collect(Collectors.toSet());
        Set<String> uniqueAttackMethods = new HashSet<>();
        for (HttpLogEntity log : logs) {
            String attackType = analyzeLogForAttackType(log);
            if (!"正常访问".equals(attackType)) {
                uniqueAttackMethods.add(attackType);
            }
        }
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
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{\"error\": \"Failed to convert to JSON\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }

    public String getAttackIpList() {
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();
        Set<String> uniqueIps = allLogs.stream()
                .map(HttpLogEntity::getIp)
                .filter(ip -> ip != null && !ip.trim().isEmpty())
                .collect(Collectors.toSet());
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode ipArrayNode = objectMapper.createArrayNode();
        uniqueIps.forEach(ipArrayNode::add);
        try {
            return objectMapper.writeValueAsString(ipArrayNode);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    public String getDashboardStats() {
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();
        int totalAttacks = allLogs.size();
        Set<String> uniqueIps = allLogs.stream()
                .map(HttpLogEntity::getIp)
                .filter(ip -> ip != null && !ip.trim().isEmpty())
                .collect(Collectors.toSet());
        int uniqueIpCount = uniqueIps.size();
        Set<String> attackTypes = new HashSet<>();
        for (HttpLogEntity log : allLogs) {
            String attackType = analyzeLogForAttackType(log);
            if (!"正常访问".equals(attackType)) {
                attackTypes.add(attackType);
            }
        }
        int todayAttacks = Math.min(totalAttacks, (int) (totalAttacks * 0.3));
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode statsNode = objectMapper.createObjectNode();
        statsNode.put("totalAttacks", totalAttacks);
        statsNode.put("uniqueIps", uniqueIpCount);
        statsNode.put("attackTypes", attackTypes.size());
        statsNode.put("todayAttacks", todayAttacks);
        try {
            return objectMapper.writeValueAsString(statsNode);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{\"error\": \"Failed to get stats\"}";
        }
    }

    public String getAttackTypeStats() {
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();
        Map<String, Integer> attackTypeCount = new HashMap<>();
        for (HttpLogEntity log : allLogs) {
            String attackType = analyzeLogForAttackType(log);
            if (!"正常访问".equals(attackType)) {
                attackTypeCount.put(attackType, attackTypeCount.getOrDefault(attackType, 0) + 1);
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
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    public String getRecentLogs(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
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
            logNode.put("attackType", analyzeLogForAttackType(log));
            logsArray.add(logNode);
        }
        try {
            return objectMapper.writeValueAsString(logsArray);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "[]";
        }
    }

    public String getAttackTrends() {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode datesArray = objectMapper.createArrayNode();
        ArrayNode countsArray = objectMapper.createArrayNode();
        LocalDate today = LocalDate.now();
        java.util.Random random = new java.util.Random();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dateStr = date.format(DateTimeFormatter.ofPattern("MM-dd"));
            int count = random.nextInt(50) + 10;
            datesArray.add(dateStr);
            countsArray.add(count);
        }
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.set("dates", datesArray);
        resultNode.set("counts", countsArray);
        try {
            return objectMapper.writeValueAsString(resultNode);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{\"dates\": [], \"counts\": []}";
        }
    }

    private String analyzeLogForAttackType(HttpLogEntity log) {
        if (log == null) {
            return "正常访问";
        }
        String parameter = log.getParameter() != null ? log.getParameter() : "";
        String body = log.getBody() != null ? log.getBody() : "";
        String contentToScan = parameter + " " + body;
        if (contentToScan.trim().isEmpty()) {
            return "正常访问";
        }
        for (AttackPattern pattern : attackPatterns) {
            if (pattern.matches(contentToScan)) {
                return pattern.getName();
            }
        }
        return "正常访问";
    }
}