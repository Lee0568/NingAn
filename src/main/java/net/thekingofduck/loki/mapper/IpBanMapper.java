package net.thekingofduck.loki.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import net.thekingofduck.loki.entity.IpBanEntity;

@Mapper
public interface IpBanMapper {

    // 修正 findByIpAddress 方法：
    // 1. 明确选择需要的字段 (ip, isBan)。
    // 2. 添加 LIMIT 1 来确保只返回一条记录。
    //    这里假设 httplog 表有 'id' 字段作为主键，或者 'time' 字段作为记录时间。
    //    您可以选择按 id 或 time 降序或升序排序后取第一条，因为 isBan 字段是统一的。
    @Select("SELECT ip, isBan FROM httplog WHERE ip = #{ip} ORDER BY id DESC LIMIT 1")
    // 或者，如果您更倾向于使用时间戳字段，并且有 'time' 字段：
    // @Select("SELECT ip, isBan FROM httplog WHERE ip = #{ip} ORDER BY time DESC LIMIT 1")
    IpBanEntity findByIpAddress(@Param("ip") String ipAddress); // 使用 @Param 确保参数名匹配

    /**
     * 将指定 IP 的封禁状态设置为 0 (解封)。
     * 针对表 `httplog` 和字段 `isBan` 进行操作。
     *
     * @param ip 要解封的 IP 地址
     * @return 更新的记录数
     */
    @Update("UPDATE httplog SET isBan = 0 WHERE ip = #{ip}")
    int updateIpStatus0(@Param("ip") String ip);

    /**
     * 将指定 IP 的封禁状态设置为 1 (封禁)。
     * 针对表 `httplog` 和字段 `isBan` 进行操作。
     *
     * @param ip 要封禁的 IP 地址
     * @return 更新的记录数
     */
    @Update("UPDATE httplog SET isBan = 1 WHERE ip = #{ip}")
    int updateIpStatus1(@Param("ip") String ip);
}