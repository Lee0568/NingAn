package net.thekingofduck.loki.service;

import net.thekingofduck.loki.entity.IpBanEntity;
import net.thekingofduck.loki.mapper.IpBanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class IpBanService {

    @Autowired
    private IpBanMapper ipBanMapper;

    /**
     * 判断一个IP地址是否被封禁。
     * 从 httplog 表中查询该IP的 isBan 字段。
     *
     * @param ip 要查询的IP地址
     * @return 如果IP被封禁则返回 true，否则返回 false。
     */
    public boolean isIpBanned(String ip) {
        // 由于 httplog 表中一个IP可能有N条记录，我们需要确定查询逻辑
        // 假设 findByIpAddress 已经调整为返回该IP的最新或有效 isBan 状态 (如只返回 Integer)
        // 或者 IpBanEntity 已经适配 httplog 表的 ip_address 和 isBan 字段。

        // 假设 IpBanMapper.findByIpAddress 返回的是 IpBanEntity 且其 status 字段映射到 isBan
        // 且该方法只会返回一条记录（例如 LIMIT 1 并按时间排序）
        IpBanEntity ipBan = ipBanMapper.findByIpAddress(ip);
        // 如果 findByIpAddress 没找到记录 (比如这个IP从未访问过)，理论上不是被封禁
        // 但您说“IP不可能不存在”，所以这里我们主要关注 `isBan` 的值。
        return ipBan != null && ipBan.getIsBan() != null && ipBan.getIsBan() == 1;
    }


    /**
     * 封禁指定的IP地址。
     * 将 httplog 表中对应IP的 isBan 字段设置为 1。
     * 假设该IP的记录在表中总是存在的。
     *
     * @param ip 要封禁的IP地址
     *
     */
    public void banIp(String ip) { // reason 参数现在可能不直接使用，除非 httplog 表有此字段
        // 直接调用Mapper方法更新 isBan 字段为 1
        // 注意：这里我们不再处理 ipBan == null 的情况
        ipBanMapper.updateIpStatus1(ip); // 新增一个专门的Mapper方法
        // 如果需要记录 reason，但 httplog 表没有此字段，您可能需要：
        // 1. 在 httplog 表中添加 reason 字段。
        // 2. 另外维护一个独立的 ip_bans 表来存储封禁的详细信息。
    }

    /**
     * 解封指定的IP地址。
     * 将 httplog 表中对应IP的 isBan 字段设置为 0。
     * 假设该IP的记录在表中总是存在的。
     *
     * @param ip 要解封的IP地址
     */
    public void unbanIp(String ip) {
        // 直接调用Mapper方法更新 isBan 字段为 0
        // 注意：这里我们不再处理 ipBan != null 的情况
        ipBanMapper.updateIpStatus0(ip); // 使用您提供的 updateIpStatus0 方法
    }
}