package net.thekingofduck.ningan.service;

import net.thekingofduck.ningan.entity.LogEvent;
import net.thekingofduck.ningan.mapper.LogEventMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class LogEventService {

    @Autowired
    private LogEventMapper logEventMapper;

    @PostConstruct
    public void init() {
        logEventMapper.createTable();
    }

    public void saveLogEvent(LogEvent logEvent) {
        logEventMapper.insert(logEvent);
    }
}