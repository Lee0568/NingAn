package net.thekingofduck.loki.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;


@Getter
@Setter
@ToString
@Component
public class HttpLogEntity {
    private int id;
    private String ip;
    private String method;
    private String path;
    private String parameter;
    private String headers;
    private String body;
    private String time;
    private String username = "xxx";
    private String password = "xxx";
    private String canvasId;
}
