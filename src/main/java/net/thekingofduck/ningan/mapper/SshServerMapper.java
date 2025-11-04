package net.thekingofduck.ningan.mapper;

import net.thekingofduck.ningan.entity.SshServerEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SshServerMapper {
    
    /**
     * 创建sshserver表
     */
    @Update("CREATE TABLE IF NOT EXISTS sshserver (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "host TEXT NOT NULL, " +
            "port INTEGER NOT NULL, " +
            "username TEXT NOT NULL, " +
            "password TEXT NOT NULL, " +
            "remark TEXT, " +
            "createTime TEXT)")
    void createTable();
    
    /**
     * 添加SSH服务器信息
     */
    @Insert("INSERT INTO sshserver(host, port, username, password, remark, createTime) VALUES (#{host}, #{port}, #{username}, #{password}, #{remark}, #{createTime})")
    Integer addServer(SshServerEntity server);
    
    /**
     * 获取所有SSH服务器信息
     */
    @Select("SELECT * FROM sshserver ORDER BY id DESC")
    List<SshServerEntity> getAllServers();
    
    /**
     * 根据ID删除SSH服务器信息
     */
    @Delete("DELETE FROM sshserver WHERE id = #{id}")
    Integer deleteServerById(int id);
    
    /**
     * 更新SSH服务器信息
     */
    @Update("UPDATE sshserver SET host=#{host}, port=#{port}, username=#{username}, password=#{password}, remark=#{remark} WHERE id=#{id}")
    Integer updateServer(SshServerEntity server);
}