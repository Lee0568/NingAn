package net.thekingofduck.ningan.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ToString
@Component
public class LogEvent {
    private Integer id;
    private String timestamp;
    private String ip;
    private String username;
    private String password;
    private String data; // 通用数据字段，用于存储额外信息
    private String type;
    private String userAgent;
    private String referrer;
}