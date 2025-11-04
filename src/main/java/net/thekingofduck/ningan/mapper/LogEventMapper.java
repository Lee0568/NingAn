package net.thekingofduck.ningan.mapper;

import net.thekingofduck.ningan.entity.LogEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LogEventMapper {

    @Update("CREATE TABLE IF NOT EXISTS log_events (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "timestamp TEXT NOT NULL, " +
            "ip TEXT, " +
            "username TEXT, " +
            "password TEXT, " +
            "data TEXT, " +
            "type TEXT, " +
            "userAgent TEXT, " +
            "referrer TEXT)")
    void createTable();

    @Insert("INSERT INTO log_events(timestamp, ip, username, password, data, type, userAgent, referrer) " +
            "VALUES (#{timestamp}, #{ip}, #{username}, #{password}, #{data}, #{type}, #{userAgent}, #{referrer})")
    void insert(LogEvent logEvent);
}