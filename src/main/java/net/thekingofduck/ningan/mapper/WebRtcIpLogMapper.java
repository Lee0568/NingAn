package net.thekingofduck.ningan.mapper;

import net.thekingofduck.ningan.entity.WebRtcIpLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WebRtcIpLogMapper {

    @Update("CREATE TABLE IF NOT EXISTS webrtc_iplog (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "canvasId TEXT, " +
            "type TEXT, " +
            "address TEXT NOT NULL, " +
            "method TEXT, " +
            "path TEXT, " +
            "headers TEXT, " +
            "time TEXT, " +
            "userAgent TEXT)")
    void createTable();

    @Insert("INSERT INTO webrtc_iplog(canvasId, type, address, method, path, headers, time, userAgent) " +
            "VALUES (#{canvasId}, #{type}, #{address}, #{method}, #{path}, #{headers}, #{time}, #{userAgent})")
    void insert(WebRtcIpLog log);
}