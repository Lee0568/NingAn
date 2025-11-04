package net.thekingofduck.ningan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.thekingofduck.ningan.common.AttackPattern;
import net.thekingofduck.ningan.entity.CanvasEnity;
import net.thekingofduck.ningan.entity.HttpLogEntity;
import net.thekingofduck.ningan.mapper.HttpLogMapper;
import net.thekingofduck.ningan.mapper.IpBanMapper;
import net.thekingofduck.ningan.mapper.SecuritySettingMapper;
import net.thekingofduck.ningan.model.BlockedIp;
import net.thekingofduck.ningan.model.SecuritySetting;
import net.thekingofduck.ningan.repository.SecuritySettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value; // 不再需要
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
// import org.springframework.web.client.RestTemplate; // 不再需要 RestTemplate

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private final List<AttackPattern> attackPatterns;
    private static final int SMS_COOLDOWN_MINUTES = 30;
    private long lastSmsSentTimestamp = 0;

    public HttpLogService() {
        attackPatterns = new ArrayList<>();
        // XSS 攻击模式 (优先级提前)
        attackPatterns.add(new AttackPattern("XSS攻击", "(?i)(<\\/?\\s*script\\b[^>]*>|javascript:|on[a-z]+\\s*=|expression\\(|<\\s*svg\\/onload|<\\s*img[^>]+src\\s*=|data:text\\/html)", Arrays.asList("script", "alert", "javascript", "onerror", "onload", "confirm", "prompt", "src", "svg")));
        // SQL 注入模式 (优先级调后)
        attackPatterns.add(new AttackPattern("SQL注入", "(?i)(union\\s+select|select\\s+.*\\s+from|information_schema|xp_cmdshell|declare\\s+@v|exec\\s+|sleep\\s*\\(|\\bwaitfor\\s+delay\\b|benchmark\\s*\\(|load_file\\s*\\(|outfile\\s*|dumpfile\\s*|utl_inaddr\\.get_host_name|utl_http\\.request|dbms_pipe\\.receive_message|from\\s+[a-z_]+\\s+where\\s+\\w+=\\w+|\\b(?:OR|AND|XOR)\\b\\s*['\"\\d]+\\s*[=><!]\\s*['\"\\d]+|['\"]\\s*(?:OR|AND|XOR)\\s*['\"]?[a-z0-9_.]*['\"]?\\s*[=><!]\\s*['\"]?[a-z0-9_.]*['\"]?|\\b(OR|AND)\\s*\\d+=\\d+\\b|\\b(OR|AND)\\s*\\w+=\\w+\\b|--|#|\\/\\*|;|\\b(insert|update|delete)\\b.*\\bwhere\\b|convert\\s*\\(|cast\\s*\\(|ascii\\s*\\(|substring\\s*\\(|mid\\s*\\(|len\\s*\\(|length\\s*\\(|char\\s*\\(|concat\\s*\\(|schema_name\\s*|table_name\\s*|column_name\\s*)", Arrays.asList("select", "union", "or", "and", "from", "where", "information_schema", "exec", "xp_cmdshell", "sleep", "waitfor", "benchmark", "load_file", "outfile", "dumpfile", "insert", "update", "delete", "convert", "cast", "ascii", "substring", "mid", "len", "length", "char", "concat", "schema_name", "table_name", "column_name")));
        // 文件包含模式
        attackPatterns.add(new AttackPattern("文件包含", "(?i)(php:\\/\\/filter|file:\\/\\/|data:\\/\\/|phar:\\/\\/|zip:\\/\\/|compress\\.zlib:\\/\\/|glob:\\/\\/|\\/etc\\/passwd|\\/proc\\/self\\/environ|\\.\\.\\/|%2e%2e%2f|%252e%252e%252f)", Arrays.asList("php://", "file://", "/etc/passwd", "../")));
        // SSRF (Server-Side Request Forgery)
        attackPatterns.add(new AttackPattern("SSRF", "(?i)(file:\\/\\/|gopher:\\/\\/|dict:\\/\\/|ftp:\\/\\/|http[s]?:\\/\\/(localhost|127\\.0.0\\.1|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+))", Arrays.asList("file://", "gopher://", "dict://")));
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

        // 1. 定义您的账户信息 (硬编码未变)
        String username = "qdgqidgqd";
        String password = "123456asd*X";
        String signature = "【柠安安全蜜罐】";

        // 2. 构造填充了真实数据的短信内容 (不包含签名)
        String content = String.format(
                "您的网站在过去 %d 分钟内检测到 %d 次攻击，已超过 %d 次的阈值。请立即检查系统安全！",
                timeWindowMinutes,
                attackCount,
                attackThreshold
        );

        // 3. 将签名和内容拼接成最终要发送的完整内容
        String fullContent = signature + content;

        // 4. 构造请求参数，严格遵循官方文档
        String httpUrl = "http://api.smsbao.com/sms";
        StringBuffer httpArg = new StringBuffer();

        try {
            String passwordMd5 = md5(password);

            httpArg.append("u=").append(username).append("&");
            httpArg.append("p=").append(passwordMd5).append("&");
            httpArg.append("m=").append(phoneNumber).append("&");
            httpArg.append("c=").append(encodeUrlString(fullContent, "UTF-8"));

            System.out.println("【短信提醒】准备发送短信到: " + phoneNumber);

            // 调用官方文档的 request 方法
            String result = request(httpUrl, httpArg.toString());

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

    /**
     * 【官方文档实现】MD5加密
     */
    public static String md5(String plainText) {
        StringBuffer buf = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes(StandardCharsets.UTF_8)); // 使用 UTF-8 确保一致性
            byte b[] = md.digest();
            int i;
            buf = new StringBuffer("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if (i < 0)
                    i += 256;
                if (i < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return buf != null ? buf.toString() : "";
    }

    /**
     * 【官方文档实现】URL 编码
     */
    public static String encodeUrlString(String str, String charset) {
        String strret = null;
        if (str == null)
            return str;
        try {
            strret = java.net.URLEncoder.encode(str, charset);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return strret;
    }

    /**
     * 【官方文档实现】发送请求
     */
    public static String request(String httpUrl, String httpArg) {
        BufferedReader reader = null;
        String result = null;
        StringBuffer sbf = new StringBuffer();
        httpUrl = httpUrl + "?" + httpArg;

        try {
            URL url = new URL(httpUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            InputStream is = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String strRead = reader.readLine();
            if (strRead != null) {
                sbf.append(strRead);
                while ((strRead = reader.readLine()) != null) {
                    sbf.append("\n");
                    sbf.append(strRead);
                }
            }
            if (reader != null) { // 增加对 reader 的 null 检查
                reader.close();
            }
            result = sbf.toString();
        } catch (Exception e) {
            // 在 Spring 容器中，这里是 Service 层，不应该静态捕获，但为了严格遵循官方文档的结构，我们保留
            e.printStackTrace();
        }
        return result;
    }

    // ... 后续代码不变 ...

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
            String body = log.getBody();
            if (body == null || body.trim().isEmpty()) continue;
            
            for (AttackPattern pattern : attackPatterns) {
                if (pattern.matches(body)) {
                    attackTypes.add(pattern.getName());
                }
            }
        }
        
        // 统计今日攻击数
        java.time.LocalDate today = java.time.LocalDate.now();
        int todayAttacks = (int) allLogs.stream()
                .filter(log -> {
                    try {
                        java.time.LocalDateTime logDateTime = java.time.LocalDateTime.parse(log.getTime(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        return logDateTime.toLocalDate().isEqual(today);
                    } catch (Exception e) {
                        // 日志时间格式不正确，忽略此条日志
                        return false;
                    }
                })
                .count();
        
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
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();

        // 统计每天的攻击次数
        java.util.Map<java.time.LocalDate, Long> dailyAttackCounts = allLogs.stream()
                .filter(log -> log.getTime() != null && !log.getTime().trim().isEmpty())
                .map(log -> {
                    try {
                        return java.time.LocalDateTime.parse(log.getTime(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
                    } catch (Exception e) {
                        return null; // 忽略解析失败的日志
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(java.util.function.Function.identity(), java.util.stream.Collectors.counting()));

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode datesArray = objectMapper.createArrayNode();
        ArrayNode countsArray = objectMapper.createArrayNode();
        
        java.time.LocalDate today = java.time.LocalDate.now();
        
        // 获取最近7天的数据，包括今天
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            String dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
            long count = dailyAttackCounts.getOrDefault(date, 0L);
            
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