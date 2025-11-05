package net.thekingofduck.ningan.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ToString
@Component
public class WebRtcIpLog {
    private Integer id;
    private String canvasId;
    private String type;
    private String address;
    private String method;
    private String path;
    private String headers;
    private String time;
    private String userAgent;
}