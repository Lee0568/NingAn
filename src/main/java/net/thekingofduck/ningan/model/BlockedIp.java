package net.thekingofduck.ningan.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "blocked_ip")
public class BlockedIp {

    @Id
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private String expiresAt;

    @Column(name = "block_mode", nullable = false)
    private String blockMode;

    // --- ▼▼▼ 修改注解中的列名 ▼▼▼ ---
    @Column(name = "canvas_id") // 将 "canvasId" 修改为 "canvas_id"
    private String canvasId;
    // --- ▲▲▲ 修改注解中的列名 ▲▲▲ ---

    // --- Getters and Setters (这部分无需改动) ---
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getBlockMode() { return blockMode; }
    public void setBlockMode(String blockMode) { this.blockMode = blockMode; }

    public String getCanvasId() { return canvasId; }
    public void setCanvasId(String canvasId) { this.canvasId = canvasId; }
}