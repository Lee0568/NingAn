package net.thekingofduck.loki.service;

import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.entity.UserInfoEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Autowired
    private HttpLogMapper getUserInfo;

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

    /**
     * 获取表格员工信息
     */
    public Map<String, Object> getUserInfoPaged(Integer page, Integer limit, String query) {
        // 1. 参数校验和默认值设置
        if (page == null || page < 1) {
            page = 1;
        }
        if (limit == null || limit < 1) {
            limit = 10;
        }

        // 2. 计算 offset（偏移量），这是关键！
        // page 从 1 开始，所以第一页的 offset 是 (1-1)*limit = 0
        int offset = (page - 1) * limit;

        // 3. 获取总记录数 (带搜索条件)
        Long totalCount = getUserInfo.getUserInfoCount(query); // 调用修正后的方法名，并传入 query

        // 4. 计算总页数
        long totalPages = (long) Math.ceil((double) totalCount / limit);
        if (totalPages == 0 && totalCount > 0) { // 避免 totalPages 为 0 但有数据的情况（如 limit > totalCount）
            totalPages = 1;
        }


        // 5. 获取当前页的用户数据 (带搜索条件)
        List<UserInfoEntity> userList = getUserInfo.getUserInfo(limit, offset, query); // 传入 limit, offset, query

        // 6. 封装结果
        Map<String, Object> result = new HashMap<>();
        result.put("list", userList);
        result.put("totalCount", totalCount);
        result.put("totalPages", totalPages);
        result.put("currentPage", page);
        result.put("pageSize", limit);

        return result;
    }


}
