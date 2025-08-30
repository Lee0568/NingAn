package net.thekingofduck.loki.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.thekingofduck.loki.entity.DeepSeekRequest;
import net.thekingofduck.loki.entity.DeepSeekResponse;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeepSeekService {

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HttpLogMapper httpLogMapper;

    // 定义一个静态内部记录(Record)，用于清晰地表示活跃时段的数据结构
    // 如果你的Java版本低于14，可以将其改为一个普通的 private static class
    private record ActiveTimeData(List<String> labels, int[] data) {}

    public DeepSeekService(HttpLogMapper httpLogMapper) {
        this.restTemplate = new RestTemplate();
        // 确保 RestTemplate 支持 UTF-8 编码
        this.restTemplate.getMessageConverters()
                .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.objectMapper = new ObjectMapper();
        this.httpLogMapper = httpLogMapper;
    }

    /**
     * [核心修改]
     * 新的工作流程：先在Java中分析数据，然后将原始数据和分析结果一起交给AI进行总结。
     */
    public String analyzeAndCallDeepSeek(String canvasId) throws JsonProcessingException {
        // 1. 根据 canvasId 从数据库获取所有日志数据
        // 注意：你需要确保 HttpLogMapper 中有 getAllInfoByCanvasId 这个方法
        List<HttpLogEntity> logs = httpLogMapper.getAllInfoByCanvasId(canvasId);

        // 2. [新增] 在Java中精确计算活跃时段数据
        ActiveTimeData activeTimeData = this.calculateActiveTimeData(logs);
        String activeTimeJson = objectMapper.writeValueAsString(activeTimeData);

        // 3. 将 HttpLogEntity 列表转换为 JSON 字符串列表，用于AI分析上下文
        List<String> logStrings = logs.stream()
                .map(log -> {
                    try {
                        return objectMapper.writeValueAsString(log);
                    } catch (JsonProcessingException e) {
                        return "{\"error\":\"json serialization failed\"}";
                    }
                })
                .collect(Collectors.toList());

        // 4. [核心修改] 构造一个更稳定、高效的提示词 (Prompt)
        String prompt = String.format("""
        你是一名顶级的网络安全专家。请根据下面提供的【网络安全日志】和已经为你统计好的【活跃时段数据】，完成以下任务：
        1. 评估黑客的整体威胁等级（"HIGH", "MEDIUM", "LOW" 或 "N/A"）。
        2. 给出一个0到100之间的精确威胁评分。
        3. 根据日志中的攻击行为，提供3-4条具体且可操作的防御建议。

        你的回答必须是一个完整的、不包含任何注释或额外文本的JSON对象。
        请将我提供给你的 `activeTimeData` 结构原样包含在你的回答中。

        JSON输出格式必须严格如下：
        {
          "threatLevel": "评估结果",
          "threatScore": 评分结果,
          "activeTimeData": %s,
          "defenseSuggestions": [
            "建议1",
            "建议2"
          ]
        }

        ---
        【已统计的活跃时段数据】:
        %s

        【网络安全日志】:
        %s
        """, activeTimeJson, activeTimeJson, String.join("\n", logStrings));

        // 5. 打印完整的提示词，便于调试
        System.out.println("--- 准备发送给 DeepSeek 的完整请求体内容开始 ---");
        System.out.println(prompt);
        System.out.println("--- 准备发送给 DeepSeek 的完整请求体内容结束 ---");

        // 6. 调用 DeepSeek API 并返回结果
        return callDeepSeek(prompt);
    }

    /**
     * [新增]
     * 一个私有辅助方法，用于在Java代码中精确统计活跃时段的攻击次数。
     * @param logs 从数据库获取的日志实体列表
     * @return 包含labels和data的ActiveTimeData对象
     */
    private ActiveTimeData calculateActiveTimeData(List<HttpLogEntity> logs) {
        // 初始化12个时间段的计数器 (0-1点, 2-3点, ..., 22-23点)
        int[] attackCounts = new int[12];
        // 假设 HttpLogEntity.getTime() 返回 "yyyy-MM-dd HH:mm:ss" 格式的字符串
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (HttpLogEntity log : logs) {
            String timeStr = log.getTime(); // 假设 getTime() 返回 String
            if (timeStr == null || timeStr.isEmpty()) {
                continue;
            }
            try {
                LocalDateTime dateTime = LocalDateTime.parse(timeStr, formatter);
                int hour = dateTime.getHour();
                int bucketIndex = hour / 2; // 计算该小时属于哪个两小时时间段
                if (bucketIndex >= 0 && bucketIndex < 12) {
                    attackCounts[bucketIndex]++;
                }
            } catch (DateTimeParseException e) {
                System.err.println("无法解析时间格式: " + timeStr);
            }
        }

        List<String> labels = Arrays.asList("0时", "2时", "4时", "6时", "8时", "10时", "12时", "14时", "16时", "18时", "20时", "22时");
        return new ActiveTimeData(labels, attackCounts);
    }


    // 原始的API调用方法，保持不变
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
                    System.out.println("--- DeepSeek API 响应对象（格式化JSON）开始 ---");
                    System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseEntity.getBody()));
                    System.out.println("--- DeepSeek API 响应对象（格式化JSON）结束 ---");
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