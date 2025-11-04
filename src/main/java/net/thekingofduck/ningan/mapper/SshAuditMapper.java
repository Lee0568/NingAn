package net.thekingofduck.ningan.mapper;

import net.thekingofduck.ningan.entity.SshAuditRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SshAuditMapper {

    @Update("CREATE TABLE IF NOT EXISTS ssh_audit (\n" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "  ts TEXT NOT NULL DEFAULT (datetime('now')),\n" +
            "  remote_ip TEXT,\n" +
            "  username TEXT,\n" +
            "  port INTEGER,\n" +
            "  event TEXT,\n" +
            "  command TEXT\n" +
            ")")
    void createTable();

    @Insert("INSERT INTO ssh_audit(remote_ip, username, port, event, command) VALUES(#{remoteIp}, #{username}, #{port}, #{event}, #{command})")
    void insert(SshAuditRecord rec);
}