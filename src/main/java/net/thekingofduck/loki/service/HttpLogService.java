package net.thekingofduck.loki.service;

import net.thekingofduck.loki.common.AttackPattern;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.entity.IpLogGroup;
import net.thekingofduck.loki.mapper.HttpLogMapper; // 假设您有一个HttpLogMapper接口
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap; // 保持IP分组顺序

@Service
public class HttpLogService {

    @Autowired
    private HttpLogMapper httpLogMapper; // 注入您的Mapper

    // 预定义的攻击模式列表
    private final List<AttackPattern> attackPatterns;

    public HttpLogService() {
        // 初始化攻击模式列表
        attackPatterns = new ArrayList<>();

        // SQL 注入模式
        attackPatterns.add(new AttackPattern(
                "SQL注入",
                "(?i)(union\\s+select|select\\s+.*\\s+from|information_schema|xp_cmdshell|declare\\s+@v|exec\\s+|sleep\\s*\\(|\\bwaitfor\\s+delay\\b|benchmark\\s*\\(|load_file\\s*\\(|outfile\\s*|dumpfile\\s*|utl_inaddr\\.get_host_name|utl_http\\.request|dbms_pipe\\.receive_message|from\\s+[a-z_]+\\s+where\\s+\\w+=\\w+|\\bOR\\s+\\d+=\\d+\\b|\\bAND\\s+\\d+=\\d+\\b|'\\s*OR\\s+'\\d+'='\\d+)",
                Arrays.asList("select", "union", "or", "and", "from", "where", "information_schema", "exec")
        ));

        // XSS 攻击模式
        attackPatterns.add(new AttackPattern(
                "XSS攻击",
                "(?i)(<script\\b[^>]*>.*?</script>|<img[^>]*src=[\"']?javascript:.*?[\"']?|<svg/onload=.*?>|on[a-z]+=[^>]*|expression\\(|data:text/html|<body[^>]*onload|alert\\s*\\(|prompt\\s*\\(|confirm\\s*\\()",
                Arrays.asList("script", "alert", "javascript", "onerror", "onload", "confirm", "prompt")
        ));

        // 命令执行/代码执行模式 (这里只列举常见部分，实际生产环境需更全面)
        attackPatterns.add(new AttackPattern(
                "命令执行",
                "(?i)(exec\\s*\\(|system\\s*\\(|shell_exec\\s*\\(|passthru\\s*\\(|proc_open\\s*\\(|pcntl_exec\\s*\\(|`.*`|curl\\s+http|wget\\s+http|rm\\s+-rf|cat\\s+/etc/passwd|bash\\s+-c)",
                Arrays.asList("exec", "system", "shell", "bash", "cat", "rm")
        ));

        // 文件包含模式 (本地/远程)
        attackPatterns.add(new AttackPattern(
                "文件包含",
                "(?i)(/etc/passwd|/proc/self/environ|\\.\\./\\.\\./|php://filter|php://input|data://|file://)",
                Arrays.asList("php://", "file://", "/etc/passwd")
        ));

        // SSRF (Server-Side Request Forgery)
        attackPatterns.add(new AttackPattern(
                "SSRF",
                "(?i)(file://|gopher://|dict://|ftp://|http[s]?://(localhost|127\\.0\\.0\\.1|10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+))",
                Arrays.asList("file://", "gopher://", "dict://")
        ));
        // 其他可能的模式...
        // attackPatterns.add(new AttackPattern("目录遍历", "(?i)(\\.\\./|%2e%2e%2f)", Arrays.asList("../")));
        // attackPatterns.add(new AttackPattern("XPath注入", "(?i)(' or '1'='1|//user[starts-with(@name,'a')]", Arrays.asList("or", "starts-with")));
    }

    /**
     * 读取日志，按IP分组，并匹配攻击模式
     *
     * @return 包含IP和检测到的攻击方式的JSON字符串
     */
    public String analyzeLogsAndDetectAttacks() {
        // 1. 从数据库读取所有日志
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();

        // 2. 按IP进行分组
        // 使用 LinkedHashMap 保持 IP 的原有顺序或第一次出现的顺序
        Map<String, IpLogGroup> ipLogGroupsMap = new LinkedHashMap<>();
        for (HttpLogEntity log : allLogs) {
            String ip = log.getIp();
            ipLogGroupsMap.computeIfAbsent(ip, k -> {
                IpLogGroup group = new IpLogGroup();
                group.setIp(k);
                group.setLogs(new ArrayList<>());
                return group;
            }).getLogs().add(log);
        }

        // 3. 遍历每个IP组，对所有body内容进行攻击模式匹配
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode resultArray = objectMapper.createArrayNode();

        for (Map.Entry<String, IpLogGroup> entry : ipLogGroupsMap.entrySet()) {
            String ip = entry.getKey();
            IpLogGroup ipLogGroup = entry.getValue();
            Set<String> detectedAttackTypes = new HashSet<>(); // 使用Set避免重复的攻击类型

            // 遍历该IP下的所有日志
            for (HttpLogEntity log : ipLogGroup.getLogs()) {
                String body = log.getBody();
                if (body == null || body.trim().isEmpty()) {
                    continue; // 跳过空的body
                }

                // 将body转为小写进行关键词匹配，提高效率
                String lowerCaseBody = body.toLowerCase();

                // 遍历所有攻击模式进行匹配
                for (AttackPattern pattern : attackPatterns) {
                    // 优先进行关键词快速匹配 (可选，但有助于效率)
                    boolean keywordFound = false;
                    for (String keyword : pattern.getKeywords()) {
                        if (lowerCaseBody.contains(keyword.toLowerCase())) {
                            keywordFound = true;
                            break;
                        }
                    }

                    // 如果关键词匹配成功，或没有关键词（直接走正则），则进行正则表达式匹配
                    if (keywordFound || pattern.getKeywords().isEmpty()) {
                        if (pattern.getRegex().matcher(body).find()) {
                            detectedAttackTypes.add(pattern.getName());
                            // 匹配到一个攻击类型即可，因为我们关心的是“是否检测到某种攻击”，而不是命中次数
                            // 如果一个body同时命中多种模式，这里会都记录下来
                        }
                    }
                }
            }

            // --- JSON 构建部分 ---
            ObjectNode ipResultNode = objectMapper.createObjectNode();
            ipResultNode.put("ip", ip);

            // 始终创建一个 ArrayNode 来存放攻击类型
            ArrayNode attackTypesArray = objectMapper.createArrayNode();
            if (detectedAttackTypes.isEmpty()) {
                attackTypesArray.add("未检测到攻击"); // 将 "未检测到攻击" 作为数组的一个元素
            } else {
                for (String type : detectedAttackTypes) {
                    attackTypesArray.add(type);
                }
            }
            ipResultNode.set("attack_types", attackTypesArray); // 使用 set 方法设置 ArrayNode

            resultArray.add(ipResultNode);
        }

        try {
            String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultArray);
            // 将生成的 JSON 字符串打印到控制台
            System.out.println("--- Generated JSON Output (for debugging) ---");
            System.out.println(jsonOutput);
            System.out.println("-------------------------------------------");
            return jsonOutput;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            // 打印错误信息，但确保返回一个有效的 JSON 格式错误消息
            return "{\"error\": \"Failed to convert to JSON\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
}