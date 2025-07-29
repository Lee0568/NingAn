package net.thekingofduck.loki.service;

import net.thekingofduck.loki.common.AttackPattern;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.entity.IpLogGroup;
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
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

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
     * 读取日志，按IP分组，并匹配攻击模式
     *
     * @return 包含IP和检测到的攻击方式的JSON字符串 (只包含有攻击的IP)
     */
    public String analyzeLogsAndDetectAttacks() {
        // 1. 从数据库读取所有日志
        List<HttpLogEntity> allLogs = httpLogMapper.selectAllLogs();

        // 2. 按IP进行分组
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

                String lowerCaseBody = body.toLowerCase();

                for (AttackPattern pattern : attackPatterns) {
                    boolean keywordFound = false;
                    for (String keyword : pattern.getKeywords()) {
                        if (lowerCaseBody.contains(keyword.toLowerCase())) {
                            keywordFound = true;
                            break;
                        }
                    }

                    if (keywordFound || pattern.getKeywords().isEmpty()) {
                        if (pattern.matches(body)) {
                            detectedAttackTypes.add(pattern.getName());
                        }
                    }
                }
            }

            // **** 核心修改开始 ****
            // 只有当 detectedAttackTypes 不为空时，才将该 IP 的数据添加到结果数组中
            if (!detectedAttackTypes.isEmpty()) {
                ObjectNode ipResultNode = objectMapper.createObjectNode();
                ipResultNode.put("ip", ip);

                ArrayNode attackTypesArray = objectMapper.createArrayNode();
                for (String type : detectedAttackTypes) {
                    attackTypesArray.add(type);
                }
                ipResultNode.set("attack_types", attackTypesArray);

                resultArray.add(ipResultNode);
            }
            // **** 核心修改结束 ****
        }

        try {
            String jsonOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultArray);
            return jsonOutput;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            e.printStackTrace();
            return "{\"error\": \"Failed to convert to JSON\", \"details\": \"" + e.getMessage() + "\"}";
        }
    }
}