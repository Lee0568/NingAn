package net.thekingofduck.loki.mapper;

import net.thekingofduck.loki.entity.IpBanEntity;
import net.thekingofduck.loki.model.BlockedIp; // 注意：导入的是 model 包下的 BlockedIp
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 负责 blocked_ip 表相关的数据操作，以及部分关联查询。
 */
@Mapper
public interface IpBanMapper {

    // (此方法不受影响，保持不变)
    @Select("SELECT ip, isBan FROM httplog WHERE ip = #{ip} ORDER BY id DESC LIMIT 1")
    IpBanEntity findByIpAddress(@Param("ip") String ipAddress);

    // --- ▼▼▼ 修改 SQL 中的列名 ▼▼▼ ---
    @Delete("DELETE FROM blocked_ip WHERE canvas_id = #{canvasId}")
    int updateIpStatus0(@Param("canvasId") String canvasId);

    // --- ▼▼▼ 修改 SQL 中的列名 ▼▼▼ ---
    @Insert("INSERT OR REPLACE INTO blocked_ip (ip_address, expires_at, block_mode, canvas_id) " +
            "SELECT DISTINCT ip, '9999-12-31 23:59:59', '手动', #{canvasId} " +
            "FROM httplog " +
            "WHERE canvasId = #{canvasId} AND ip IS NOT NULL AND ip != ''")
    int updateIpStatus1(@Param("canvasId") String canvasId);

    // --- ▼▼▼ 修改 SQL 和 @Result 中的列名 ▼▼▼ ---
    @Results({
            @Result(property = "ipAddress", column = "ip_address"),
            @Result(property = "expiresAt", column = "expires_at"),
            @Result(property = "blockMode", column = "block_mode"),
            @Result(property = "canvasId", column = "canvas_id") // 此处也修改
    })
    @Select("SELECT ip_address, expires_at, block_mode, canvas_id FROM blocked_ip") // 此处也修改
    List<BlockedIp> findAllBlockedIps();

    /**
     * 根据IP地址删除一条封禁记录。
     * @param ipAddress 要解封的IP地址
     * @return 删除的行数 (通常是 1 或 0)
     */
    @Delete("DELETE FROM blocked_ip WHERE ip_address = #{ipAddress}")
    int deleteByIpAddress(@Param("ipAddress") String ipAddress);
}