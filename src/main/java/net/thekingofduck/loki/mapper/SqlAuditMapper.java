package net.thekingofduck.loki.mapper;

import net.thekingofduck.loki.entity.SqlAuditRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SqlAuditMapper {

    @Update("CREATE TABLE IF NOT EXISTS sql_audit (\n" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
            "  ts TEXT NOT NULL DEFAULT (datetime('now')),\n" +
            "  remote_ip TEXT,\n" +
            "  username TEXT,\n" +
            "  port INTEGER,\n" +
            "  query TEXT\n" +
            ")")
    void createTable();

    @Insert("INSERT INTO sql_audit(remote_ip, username, port, query) VALUES(#{remoteIp}, #{username}, #{port}, #{query})")
    void insert(SqlAuditRecord rec);
}