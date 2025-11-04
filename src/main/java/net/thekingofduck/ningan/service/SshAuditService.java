package net.thekingofduck.ningan.service;

import net.thekingofduck.ningan.entity.SshAuditRecord;
import net.thekingofduck.ningan.mapper.SshAuditMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class SshAuditService {
    @Autowired
    private SshAuditMapper mapper;

    @PostConstruct
    public void init(){
        mapper.createTable();
    }

    public void logConnect(String remoteIp, String username, int port){
        SshAuditRecord r = new SshAuditRecord();
        r.setRemoteIp(remoteIp);
        r.setUsername(username);
        r.setPort(port);
        r.setEvent("connect");
        mapper.insert(r);
    }

    public void logCommand(String remoteIp, String username, int port, String command){
        SshAuditRecord r = new SshAuditRecord();
        r.setRemoteIp(remoteIp);
        r.setUsername(username);
        r.setPort(port);
        r.setEvent("command");
        r.setCommand(command);
        mapper.insert(r);
    }

    public void logDisconnect(String remoteIp, String username, int port){
        SshAuditRecord r = new SshAuditRecord();
        r.setRemoteIp(remoteIp);
        r.setUsername(username);
        r.setPort(port);
        r.setEvent("disconnect");
        mapper.insert(r);
    }
}