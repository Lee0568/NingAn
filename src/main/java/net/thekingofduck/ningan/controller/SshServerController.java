package net.thekingofduck.ningan.controller;

import net.thekingofduck.ningan.entity.SshServerEntity;
import net.thekingofduck.ningan.service.SshServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ssh")
public class SshServerController {
    
    @Autowired
    private SshServerService sshServerService;
    
    /**
     * 添加SSH服务器
     */
    @PostMapping("/server")
    public Map<String, Object> addServer(@RequestBody SshServerEntity server) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查是否已存在相同的SSH服务器
            SshServerEntity existingServer = sshServerService.getExistingServer(server);
            
            if (existingServer != null) {
                // 如果已存在，返回提示信息，由前端决定是否更新
                result.put("code", 202); // 使用202状态码表示需要进一步操作
                result.put("message", "服务器已存在，是否更新密码？");
                result.put("data", existingServer);
            } else {
                // 如果不存在，则正常添加
                Integer rows = sshServerService.addServer(server);
                if (rows > 0) {
                    result.put("code", 200);
                    result.put("message", "添加成功");
                } else {
                    result.put("code", 500);
                    result.put("message", "添加失败");
                }
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "添加失败: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * 更新SSH服务器密码
     */
    @PutMapping("/server")
    public Map<String, Object> updateServer(@RequestBody SshServerEntity server) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer rows = sshServerService.updateServer(server);
            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "更新成功");
            } else {
                result.put("code", 500);
                result.put("message", "更新失败");
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "更新失败: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * 获取所有SSH服务器
     */
    @GetMapping("/servers")
    public Map<String, Object> getAllServers() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<SshServerEntity> servers = sshServerService.getAllServers();
            result.put("code", 200);
            result.put("message", "获取成功");
            result.put("data", servers);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "获取失败: " + e.getMessage());
            result.put("data", null);
        }
        return result;
    }
    
    /**
     * 删除SSH服务器
     */
    @DeleteMapping("/server/{id}")
    public Map<String, Object> deleteServer(@PathVariable int id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer rows = sshServerService.deleteServerById(id);
            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "删除成功");
            } else {
                result.put("code", 500);
                result.put("message", "删除失败");
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }
}