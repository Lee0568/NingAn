package net.thekingofduck.loki.service;

import net.thekingofduck.loki.entity.DeepSeekRequest;
import net.thekingofduck.loki.entity.DeepSeekResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import java.nio.charset.StandardCharsets;
import java.util.List;

// 为了打印对象为JSON字符串，可以引入Jackson ObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;


@Service
public class DeepSeekService {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // 添加 ObjectMapper

    public DeepSeekService() {
        this.restTemplate = new RestTemplate();
        // 移除所有默认的 StringHttpMessageConverter，并添加一个强制 UTF-8 的
        // 这对于你接收到 DeepSeek 返回的原始 JSON 字符串（如果作为String.class获取）是重要的。
        // 然而，当目标是 DeepSeekResponse.class 时，Jackson（默认的JSON转换器）会处理编码。
        // 但为了通用性和避免其他潜在乱码，保留此配置依然是个好习惯。
        this.restTemplate.getMessageConverters().removeIf(
                converter -> converter instanceof StringHttpMessageConverter
        );
        this.restTemplate.getMessageConverters().add(
                new StringHttpMessageConverter(StandardCharsets.UTF_8)
        );
        this.objectMapper = new ObjectMapper(); // 初始化 ObjectMapper
    }

    public String callDeepSeek(String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        // 设置请求体的 Content-Type，并显式指定 UTF-8
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.set("Authorization", "Bearer " + apiKey);

        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel("deepseek-chat");
        request.setMessages(List.of(new DeepSeekRequest.Message("user", userMessage)));

        HttpEntity<DeepSeekRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<DeepSeekResponse> responseEntity; // 声明为 DeepSeekResponse 类型

        try {
            // *** 关键修改：直接将响应映射到 DeepSeekResponse.class ***
            // RestTemplate 会自动使用 Jackson 将 JSON 响应体解析为 DeepSeekResponse 对象。
            // 只要 Jackson 配置正确（通常Spring Boot默认就是），中文就不会乱码。
            responseEntity = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, DeepSeekResponse.class);

            // 打印解析后的 DeepSeekResponse 对象为 JSON 字符串到控制台
            if (responseEntity.getBody() != null) {
                try {
                    System.out.println("--- DeepSeek API 解析后对象（JSON表示）开始 ---");
                    // 使用 ObjectMapper 将对象转换为格式化的 JSON 字符串
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
                // 如果状态码非2xx，尝试获取原始错误体（此时可能需要再次请求或更复杂的错误流处理）
                // 为了简化，这里仅抛出基于状态码的错误
                String errorBody = "无法获取详细错误体";
                // 在此情境下，由于我们第一次就尝试解析为对象，若非2xx，原始JSON可能不同
                // 简单的做法是，如果非2xx，就直接使用responseEntity的getBody()，但它可能已是空或错误对象
                // 或者，如之前，再发一次请求获取原始String来做调试，但在生产不推荐
                throw new RuntimeException(
                        "调用 DeepSeek API 失败，状态码: " + responseEntity.getStatusCode() +
                                " - 响应体: " + (responseEntity.getBody() != null ? responseEntity.getBody().toString() : errorBody) // 或者尝试序列化为JSON
                );
            }

        } catch (Exception e) {
            // 捕获所有潜在的异常，包括网络错误和 JSON 解析错误
            System.err.println("调用 DeepSeek API 发生错误: " + e.getMessage());
            e.printStackTrace(); // 打印完整的堆栈信息
            throw new RuntimeException("调用 DeepSeek API 发生错误: " + e.getMessage(), e);
        }
    }
}