package net.thekingofduck.ningan.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.Date;


@Getter
@Setter
@ToString
@Component
public class UserInfoEntity {
    private int id;
    private String username;
    private String email;
    private String role;
    private String regDate;
}
