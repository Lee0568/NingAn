package net.thekingofduck.loki.mapper;

import net.thekingofduck.loki.entity.AdminUser;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminUserMapper {
    
    /**
     * 根据用户名获取管理员用户信息
     */
    @Select("SELECT * FROM admin_user WHERE username = #{username}")
    AdminUser getAdminUserByUsername(String username);
    
    /**
     * 插入新的管理员用户
     */
    @Insert("INSERT INTO admin_user(username, password, salt, role, created_time) VALUES (#{username}, #{password}, #{salt}, #{role}, #{createdTime})")
    Integer insertAdminUser(AdminUser adminUser);
    
    /**
     * 更新管理员用户密码
     */
    @Update("UPDATE admin_user SET password = #{password}, salt = #{salt} WHERE username = #{username}")
    Integer updateAdminUserPassword(AdminUser adminUser);
}