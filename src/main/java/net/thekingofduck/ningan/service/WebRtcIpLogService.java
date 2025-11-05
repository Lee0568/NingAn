package net.thekingofduck.ningan.service;

import net.thekingofduck.ningan.entity.WebRtcIpLog;
import net.thekingofduck.ningan.mapper.WebRtcIpLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class WebRtcIpLogService {

    @Autowired
    private WebRtcIpLogMapper mapper;

    @PostConstruct
    public void init() {
        mapper.createTable();
    }

    public void save(WebRtcIpLog log) {
        mapper.insert(log);
    }

    public void saveBatch(List<WebRtcIpLog> logs) {
        if (logs == null) return;
        for (WebRtcIpLog log : logs) {
            mapper.insert(log);
        }
    }
}