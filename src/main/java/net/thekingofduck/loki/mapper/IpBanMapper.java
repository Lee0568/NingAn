package net.thekingofduck.loki.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import net.thekingofduck.loki.entity.IpBanEntity; // 确保这个实体类与您的httplog表结构对应

@Mapper
public interface IpBanMapper {

    // 注意：这里的查询方法也要根据实际表名和字段调整
    // 如果 httplog 表中没有 'id', 'status', 'banned_at', 'reason' 这些字段，
    // 而只有 'ip_address' 和 'isBan'，那么 IpBanEntity 需要调整，或者另建一个 HttpLogEntity 来查询。
    // 为简化起见，这里假设 IpBanEntity 已经能对应到 httplog 表的 ip_address 和 isBan 字段
    @Select("SELECT ip_address, isBan AS status FROM httplog WHERE ip = #{ip} LIMIT 1")
    IpBanEntity findByIpAddress(String ip); // 或者返回 Integer 类型的 isBan 值

    /**
     * 将指定 IP 的封禁状态设置为 0 (解封)。
     * 针对表 `httplog` 和字段 `isBan` 进行操作。
     *
     * @param ip 要解封的 IP 地址
     * @return 更新的记录数
     */
    @Update("UPDATE httplog SET isBan = 0 WHERE ip = #{ip}")
    int updateIpStatus0(@Param("ip") String ip);

    // 如果您仍然需要通用的更新方法，且 httplog 表有 status, banned_at, reason 字段，则保留以下方法
    // 否则，可能需要删除或修改此方法以匹配 httplog 表的实际结构
    // @Update("UPDATE httplog SET isBan = #{status}, banned_at = #{bannedAt}, reason = #{reason} WHERE ip_address = #{ip}")
    // int updateIpStatus(IpBanEntity ipBan);
}