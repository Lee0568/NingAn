package net.thekingofduck.ningan.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ToString
@Component
public class AdminUser {
    private int id;
    private String username;
    private String password; // 存储加密后的密码
    private String salt;     // 盐值，用于密码加密
    private String role;
    private String createdTime;
}