package net.thekingofduck.loki.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "blocked_ip") // 明确指定表名
public class BlockedIp {

    @Id
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 新增字段，映射到'block_mode'列
    @Column(name = "block_mode", nullable = false)
    private String blockMode;

    // --- Getters and Setters ---
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getBlockMode() { return blockMode; }
    public void setBlockMode(String blockMode) { this.blockMode = blockMode; }
}