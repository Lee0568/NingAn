package net.thekingofduck.ningan.dto;

import java.util.List;

public class FingerprintLogRequest {
    private String visitorId;
    private String canvasId;
    private String method;
    private String path;
    private String headers;
    private String time;
    private String userAgent;
    private String details;

    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }
    public String getCanvasId() { return canvasId; }
    public void setCanvasId(String canvasId) { this.canvasId = canvasId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}