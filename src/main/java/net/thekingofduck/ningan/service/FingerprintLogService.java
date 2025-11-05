package net.thekingofduck.ningan.service;

import net.thekingofduck.ningan.entity.FingerprintLog;
import net.thekingofduck.ningan.mapper.FingerprintLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class FingerprintLogService {

    @Autowired
    private FingerprintLogMapper mapper;

    @PostConstruct
    public void init() {
        mapper.createTable();
    }

    public void save(FingerprintLog log) {
        mapper.insert(log);
    }

    public List<FingerprintLog> listRecent(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        return mapper.listRecent(limit);
    }
}