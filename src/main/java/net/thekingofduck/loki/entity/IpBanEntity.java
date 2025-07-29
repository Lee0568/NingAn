package net.thekingofduck.loki.entity;

import lombok.Data; // 假设您使用 Lombok 简化代码

import java.time.LocalDateTime;

@Data
public class IpBanEntity {
    private Long id;
    private String ip;
    private Integer isBan; // 0 for active, 1 for banned
}