package net.thekingofduck.loki.entity;

import lombok.Data;

@Data
public class SshAuditRecord {
    private Long id;
    private String ts;        // 由数据库默认生成 CURRENT_TIMESTAMP
    private String remoteIp;
    private String username;
    private Integer port;
    private String event;     // connect/command/disconnect
    private String command;   // 仅在 event=command 时有值
}