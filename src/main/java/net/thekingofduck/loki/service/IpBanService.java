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

        IpBanEntity ipBan = ipBanMapper.findByIpAddress(ip);
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
    public void banIp(String ip) {
        ipBanMapper.updateIpStatus1(ip);
    }

    /**
     * 解封指定的IP地址。
     * 将 httplog 表中对应IP的 isBan 字段设置为 0。
     * 假设该IP的记录在表中总是存在的。
     *
     * @param ip 要解封的IP地址
     */
    public void unbanIp(String ip) {
        ipBanMapper.updateIpStatus0(ip);
    }
}