package net.thekingofduck.loki.service;

import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author MoCaser
 *
 *
 *
 */
@Service
public class UserService {

    @Autowired
    private HttpLogMapper httpLogMapper;

    /**
     * 插入HTTP日志
     * @param httpLogEntity 日志实体
     * @return 影响行数
     */
    public Integer insertHttpLog(HttpLogEntity httpLogEntity){
        if (httpLogEntity == null) {
            return 0;
        }
        try {
            // 调用Mapper接口，传入HttpLogEntity的各个属性
            return httpLogMapper.addHttpLog(
                    httpLogEntity.getIp(),
                    httpLogEntity.getMethod(),
                    httpLogEntity.getPath(),
                    httpLogEntity.getParameter(),
                    httpLogEntity.getHeaders(),
                    httpLogEntity.getBody(),
                    httpLogEntity.getTime()
            );
        } catch (Exception e) {
            // 生产环境中应使用日志框架记录异常，例如 SLF4J + Logback
            e.printStackTrace();
            return 0; // 返回0表示插入失败
        }
    }


}
