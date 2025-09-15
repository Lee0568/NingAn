package net.thekingofduck.loki.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ToString
@Component
public class UserInfoEnity {
    private int id; // 新增时此字段为空，由数据库生成
    private String username;
    private String email;
    private String role;
    private String regDate; // 注册日期通常由后端生成

}
