package net.thekingofduck.ningan.entity;

import lombok.Data;
import lombok.NoArgsConstructor; // 添加此导入
import lombok.AllArgsConstructor; // 添加此导入

import java.util.List;

@Data
public class DeepSeekRequest {
    private String model;
    private List<Message> messages;

    @Data
    @NoArgsConstructor // 添加无参构造函数（Lombok默认行为）
    @AllArgsConstructor // 添加全参构造函数（关键！解决你的问题）
    public static class Message {
        private String role;
        private String content;
    }
}