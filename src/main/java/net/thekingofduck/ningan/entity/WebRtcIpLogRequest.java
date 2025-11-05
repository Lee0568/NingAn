package net.thekingofduck.ningan.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@ToString
@Component
public class WebRtcIpLogRequest {
    private String canvasId;
    private List<WebRtcIpRecord> ips;
    private String method;
    private String path;
    private String headers;
    private String time; // ISO8601 from frontend
    private String userAgent; // optional; if null, backend will fill from request
}