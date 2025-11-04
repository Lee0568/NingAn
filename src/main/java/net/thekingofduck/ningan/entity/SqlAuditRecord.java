package net.thekingofduck.ningan.entity;

import lombok.Data;

@Data
public class SqlAuditRecord {
    private Long id;
    private String ts;        // 默认 CURRENT_TIMESTAMP
    private String remoteIp;
    private String username;
    private Integer port;
    private String query;
}