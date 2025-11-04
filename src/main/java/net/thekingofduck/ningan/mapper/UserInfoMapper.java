package net.thekingofduck.ningan.mapper;

import net.thekingofduck.ningan.entity.UserInfoEnity;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserInfoMapper {

    /**
     * 修改员工信息
     * @return 返回受影响的行数，通常为 1 表示成功，0 表示失败
     */
    @Update("UPDATE userinfo SET username = #{username}, email = #{email}, role = #{role} WHERE id = #{id}")
    int updateUserInfo(UserInfoEnity userInfo);

    /**
     * 根据ID删除员工信息
     * @param id 要删除的员工ID
     * @return 返回受影响的行数，1表示成功，0表示未找到该ID的用户
     */
    @Delete("DELETE FROM userinfo WHERE id = #{id}")
    int deleteUserById(@Param("id") int id);

    /**
     * 添加新员工信息
     * @param userInfo 包含新员工信息的对象
     * @return 返回受影响的行数
     */
    @Insert("INSERT INTO userinfo (username, email, role, regDate) " +
            "VALUES (#{username}, #{email}, #{role}, #{regDate})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addUser(UserInfoEnity userInfo);
}
