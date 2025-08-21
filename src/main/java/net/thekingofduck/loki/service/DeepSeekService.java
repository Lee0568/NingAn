package net.thekingofduck.loki.service;

import net.thekingofduck.loki.entity.DeepSeekRequest;
import net.thekingofduck.loki.entity.DeepSeekResponse;
import net.thekingofduck.loki.entity.HttpLogEntity; // 引入 HttpLogEntity
import net.thekingofduck.loki.mapper.HttpLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors; // 引入 Collectors

@Service
public class DeepSeekService {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpLogMapper httpLogMapper;

    public DeepSeekService(HttpLogMapper httpLogMapper) {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().removeIf(
                converter -> converter instanceof StringHttpMessageConverter
        );
        this.restTemplate.getMessageConverters().add(
                new StringHttpMessageConverter(StandardCharsets.UTF_8)
        );
        this.objectMapper = new ObjectMapper();
        this.httpLogMapper = httpLogMapper;
    }

    // 修改后的方法：用于分析数据并调用 DeepSeek API
    public String analyzeAndCallDeepSeek(String canvasId) {
        // 1. 根据 canvasId 从数据库获取所有 HttpLogEntity 实体数据
        List<HttpLogEntity> logs = httpLogMapper.getAllInfoByCanvasId(canvasId);

        // 2. 将 HttpLogEntity 列表转换为 JSON 字符串列表
        List<String> logStrings = logs.stream()
                .map(log -> {
                    try {
                        // 使用 ObjectMapper 将每个 HttpLogEntity 对象转换为 JSON 字符串
                        return objectMapper.writeValueAsString(log);
                    } catch (JsonProcessingException e) {
                        // 如果转换失败，返回一个空字符串或错误提示
                        return "{}";
                    }
                })
                .collect(Collectors.toList());

        // 3. 构造传给 DeepSeek 的提示词
        String prompt = "你现在是一名安全专家，你需要评估出黑客的威胁等级(\"HIGH\", \"MEDIUM\", \"LOW\" 或 \"N/A\")和威胁评分(0~100)；活跃时段数据；根据数据分析得来的可靠防御建议。记住，你只需返回一段json格式的数据，除此之外什么都不要乱加。返回的数据格式参考如下(参数名需要对应相同)：\n" +
                "{\n" +
                "  \"threatLevel\": \"HIGH\",\n" +
                "  \"threatScore\": 95,\n" +
                "  \"activeTimeData\": {\n" +
                "    \"labels\": [\"0时\", \"2时\", \"4时\", \"6时\", \"8时\", \"10时\", \"12时\", \"14时\", \"16时\", \"18时\", \"20时\", \"22时\"],\n" +
                "    \"data\": [3, 5, 8, 12, 15, 20, 18, 10, 6, 4, 2, 1]\n" +
                "  },\n" +
                "  \"defenseSuggestions\": [\n" +
                "    \"启用WAF，增加规则以识别和阻止常见的命令执行攻击。\",\n" +
                "    \"将该IP地址列入黑名单，并监控是否有新的IP尝试类似行为。\",\n" +
                "    \"审查与此黑客ID相关的用户和会话日志，以发现潜在的恶意活动。\",\n" +
                "    \"更新和修补所有服务器和应用程序，特别是那些容易受到命令执行漏洞攻击的软件。\"\n" +
                "  ]\n" +
                "}\n" +
                "下面是需要你分析的网络安全日志：\n" +
                String.join("\n", logStrings); // 使用正确的字符串列表

        // 4. 将完整的问句打印到控制台，以便调试
        System.out.println("--- 准备发送给 DeepSeek 的完整请求体内容开始 ---");
        System.out.println(prompt);
        System.out.println("--- 准备发送给 DeepSeek 的完整请求体内容结束 ---");

        // 5. 调用 callDeepSeek 方法将提示词传递给 DeepSeek
        return callDeepSeek(prompt);
    }


    // 原始方法，不做修改
    public String callDeepSeek(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.set("Authorization", "Bearer " + apiKey);

        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel("deepseek-chat");
        request.setMessages(List.of(new DeepSeekRequest.Message("user", userMessage)));

        HttpEntity<DeepSeekRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<DeepSeekResponse> responseEntity;

        try {
            responseEntity = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, DeepSeekResponse.class);

            if (responseEntity.getBody() != null) {
                try {
                    System.out.println("--- DeepSeek API 解析后对象（JSON表示）开始 ---");
                    System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseEntity.getBody()));
                    System.out.println("--- DeepSeek API 解析后对象（JSON表示）结束 ---");
                } catch (JsonProcessingException e) {
                    System.err.println("无法将 DeepSeekResponse 对象转换为 JSON 字符串进行打印: " + e.getMessage());
                }
            }


            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                if (responseEntity.getBody().getChoices() != null && !responseEntity.getBody().getChoices().isEmpty()) {
                    DeepSeekResponse.Choice firstChoice = responseEntity.getBody().getChoices().get(0);
                    if (firstChoice.getMessage() != null && firstChoice.getMessage().getContent() != null) {
                        return firstChoice.getMessage().getContent();
                    }
                }
                throw new RuntimeException("DeepSeek API 返回的响应内容结构不符合预期或为空。");
            } else {
                String errorBody = "无法获取详细错误体";
                throw new RuntimeException(
                        "调用 DeepSeek API 失败，状态码: " + responseEntity.getStatusCode() +
                                " - 响应体: " + (responseEntity.getBody() != null ? responseEntity.getBody().toString() : errorBody)
                );
            }

        } catch (Exception e) {
            System.err.println("调用 DeepSeek API 发生错误: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("调用 DeepSeek API 发生错误: " + e.getMessage(), e);
        }
    }
}