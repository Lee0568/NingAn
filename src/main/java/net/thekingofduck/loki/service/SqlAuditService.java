package net.thekingofduck.loki.service;

import net.thekingofduck.loki.entity.SqlAuditRecord;
import net.thekingofduck.loki.mapper.SqlAuditMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class SqlAuditService {
    @Autowired
    private SqlAuditMapper mapper;

    @PostConstruct
    public void init(){
        mapper.createTable();
    }

    public void logQuery(String remoteIp, String username, int port, String query){
        SqlAuditRecord r = new SqlAuditRecord();
        r.setRemoteIp(remoteIp);
        r.setUsername(username);
        r.setPort(port);
        r.setQuery(query);
        mapper.insert(r);
    }
}