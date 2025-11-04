package net.thekingofduck.ningan.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ToString
@Component
public class SshServerEntity {
    private int id;
    private String host;
    private int port;
    private String username;
    private String password; // 加密存储
    private String remark;
    private String createTime;
}