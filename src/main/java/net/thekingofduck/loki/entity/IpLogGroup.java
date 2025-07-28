package net.thekingofduck.loki.entity;

import net.thekingofduck.loki.entity.HttpLogEntity;

import java.util.List;

public class IpLogGroup {
    private String ip;
    private List<HttpLogEntity> logs;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public List<HttpLogEntity> getLogs() {
        return logs;
    }

    public void setLogs(List<HttpLogEntity> logs) {
        this.logs = logs;
    }

    @Override
    public String toString() {
        return "IpLogGroup{" +
                "ip='" + ip + '\'' +
                ", logs=" + logs +
                '}';
    }
}