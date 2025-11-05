package net.thekingofduck.ningan.mapper;

import net.thekingofduck.ningan.entity.FingerprintLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface FingerprintLogMapper {

    @Update("CREATE TABLE IF NOT EXISTS fp_log (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "visitorId TEXT, " +
            "canvasId TEXT, " +
            "ip TEXT, " +
            "userAgent TEXT, " +
            "method TEXT, " +
            "path TEXT, " +
            "headers TEXT, " +
            "time TEXT, " +
            "details TEXT" +
            ")")
    void createTable();

    @Insert("INSERT INTO fp_log (visitorId, canvasId, ip, userAgent, method, path, headers, time, details) " +
            "VALUES (#{visitorId}, #{canvasId}, #{ip}, #{userAgent}, #{method}, #{path}, #{headers}, #{time}, #{details})")
    void insert(FingerprintLog log);

    @Select("SELECT id, visitorId, canvasId, ip, userAgent, method, path, headers, time, details " +
            "FROM fp_log ORDER BY id DESC LIMIT #{limit}")
    List<FingerprintLog> listRecent(@Param("limit") int limit);
}