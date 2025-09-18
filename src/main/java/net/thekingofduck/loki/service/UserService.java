package net.thekingofduck.loki.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.thekingofduck.loki.entity.CommandEnity;
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

    // 模拟的当前路径，与前端保持一致，这里作为Service的成员变量
    // 注意：如果是多用户并发访问，每个用户应该有独立的路径状态。
    // 简单的蜜罐系统可能不需要精细的用户会话管理，但实际应用中需要考虑。

    @Autowired
    private HttpLogMapper httpLogMapper;
    @Autowired
    private HttpLogMapper getUserInfo; // 这个命名可能有点歧义，通常Mapper接口名会直接反映其功能

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
     * 往httplog表中插入canvasId
     */
    public Integer insertCanvasId(HttpLogEntity httpLogEntity){
        if (httpLogEntity == null) {
            return 0;
        }
        try {
            // 调用Mapper接口，传入HttpLogEntity的各个属性
            return httpLogMapper.updateCanvasId(
                   httpLogEntity.getCanvasId()
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
    public Map<String, Object> getUserInfoPaged(Integer page, Integer limit, String query, String canvasId) {
        // 1. 参数校验和默认值设置
        if (page == null || page < 1) {
            page = 1;
        }
        if (limit == null || limit < 1) {
            limit = 10;
        }

        // 2. 计算 offset
        int offset = (page - 1) * limit;

        // 3. 获取总记录数
        Long totalCount = getUserInfo.getUserInfoCount(query);

        // 4. 计算总页数
        long totalPages = (long) Math.ceil((double) totalCount / limit);
        if (totalPages == 0 && totalCount > 0) {
            totalPages = 1;
        }

        // 5. 获取当前页的用户数据
        List<UserInfoEntity> userList = getUserInfo.getUserInfo(limit, offset, query);

        // 6. 封装结果
        Map<String, Object> result = new HashMap<>();
        result.put("list", userList);
        result.put("totalCount", totalCount);
        result.put("totalPages", totalPages);
        result.put("currentPage", page);
        result.put("pageSize", limit);

        // 增加一个判断，确保 canvasId 不为空时才执行更新
        if (canvasId != null && !canvasId.trim().isEmpty()) {
            try {
                httpLogMapper.updateCanvasIdForLastHttpLog(canvasId);
                System.out.println("成功更新最新日志的 canvasId: " + canvasId);
            } catch (Exception e) {
                // 在实际项目中，这里应该使用日志框架记录错误，而不是简单打印
                System.err.println("更新最新日志的 canvasId 时发生错误: " + e.getMessage());
            }
        }

        if (httpLogMapper.getCanvasIdCount(canvasId) !=0) {
            System.out.println("已存在重复canvasId记录");
        }else{
            httpLogMapper.addCanvasId(canvasId);
        }

        return result;
    }


    /**
     * 蜜罐-按照接受到的命令返回对应结果
     */
    public CommandEnity executeCommand(String command) { // 返回类型改为 CommandEnity
        CommandEnity result = new CommandEnity(); // 创建一个实体类实例
        String response = "";

        if (command == null || command.trim().isEmpty()) {
            response = "Error: Command cannot be empty.";
        } else {
            String lowerCaseCommand = command.trim().toLowerCase();

            switch (lowerCaseCommand) {
                case "ls":
                    response = "file1.txt\nfolder_a\nindex.html\nREADME.md";
                    break;
                case "whoami":
                    response = "root";
                    break;
                case "pwd":
                    response = "/var/www/html";
                    break;
                case "cat /etc/passwd":
                    response = "root:x:0:0:root:/root:/bin/bash\nlokiuser:x:1000:1000:Loki User:/home/lokiuser:/bin/bash";
                    break;
                case "id":
                    response = "uid=1000(lokiuser) gid=1000(lokiuser) groups=1000(lokiuser)";
                    break;
                case "help":
                    response = "Available commands: ls, whoami, pwd, cat /etc/passwd, id, help, exit.";
                    break;
                case "exit":
                    response = "Exiting shell. Goodbye!";
                    break;
                default:
                    response = "Command not found: '" + command + "'\nTry 'help' for available commands.";
                    break;
            }
        }
        result.setCommand(response); // 将结果设置到 CommandEnity 的 'command' 字段
        return result; // 返回 CommandEnity 对象
    }
}