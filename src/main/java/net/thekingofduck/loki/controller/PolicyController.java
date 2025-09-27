package net.thekingofduck.loki.controller;

import net.thekingofduck.loki.config.SecurityPolicyManager;
import net.thekingofduck.loki.service.HttpLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {

    @Autowired
    private SecurityPolicyManager policyManager;

    @Autowired
    private HttpLogService httpLogService;

    @PostMapping("/autoban")
    public ResponseEntity<String> saveAutoBanSettings(@RequestBody AutoBanSettingsDto settings) {
        policyManager.updatePolicy(settings.getFrequency(), settings.getDuration());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                .body("自动封禁策略已更新。");
    }

    @GetMapping("/autoban")
    public ResponseEntity<Map<String, Integer>> getAutoBanSettings() {
        int currentFrequency = policyManager.getFrequencyThreshold();
        int currentDuration = policyManager.getBanDurationMinutes();
        Map<String, Integer> currentSettings = new HashMap<>();
        currentSettings.put("frequency", currentFrequency);
        currentSettings.put("duration", currentDuration);
        return ResponseEntity.ok(currentSettings);
    }

    /**
     * 保存短信提醒策略设置 (不使用DTO，直接接收Map)。
     * 接收JSON格式: { "timeWindowMinutes": 5, "attacksAmount": 1000, "phone": "13800138000" }
     *
     * @param payload 包含策略设置的Map对象
     * @return 成功或失败的响应信息
     */
    @PostMapping("/updateSmsSettings")
    public ResponseEntity<String> saveSmsSettings(@RequestBody Map<String, Object> payload) {
        try {
            // 1. 从 Map 中提取数据
            Integer timeWindowMinutes = (Integer) payload.get("timeWindowMinutes");
            Integer attacksAmount = (Integer) payload.get("attacksAmount");
            String phone = String.valueOf(payload.get("phone")); // 使用 String.valueOf() 兼容数字和字符串

            // 2. 健壮性检查，防止传入空值
            if (timeWindowMinutes == null || attacksAmount == null || "null".equals(phone) || phone.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                        .body("请求失败：缺少必要的参数 (timeWindowMinutes, attacksAmount, phone)。");
            }

            // 3. 调用服务层方法
            httpLogService.updateSmsSettings(timeWindowMinutes, attacksAmount, phone);

            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                    .body("短信提醒策略已更新。");

        } catch (Exception e) {
            // 捕获通用异常，以防类型转换等其他错误
            return ResponseEntity.badRequest()
                    .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                    .body("请求失败：参数格式或类型不正确。请检查所有字段。");
        }
    }

    /**
     * 获取当前的短信提醒策略配置。
     * @return 包含当前配置的JSON对象
     */
    @GetMapping("/updateSmsSettings")
    public ResponseEntity<Map<String, Object>> getSmsSettings() {
        Map<String, Object> currentSettings = httpLogService.getSmsSettings();
        return ResponseEntity.ok(currentSettings);
    }

    /**
     * 获取所有被封禁的IP列表。
     * @return 返回JSON数组，每个对象包含 ipAddress, expiresAt, blockMode 字段。
     */
    @GetMapping("/blocked-ips")
    public ResponseEntity<List<Map<String, Object>>> getBlockedIps() {
        List<Map<String, Object>> blockedIps = httpLogService.getBlockedIpsForFrontend();
        return ResponseEntity.ok(blockedIps);
    }

    /**
     * 根据IP地址解封一个IP。
     *
     * @param ip 要解封的IP地址，从URL路径中获取
     * @return 成功或失败的响应信息
     */
    @DeleteMapping("/blocked-ips/{ip}")
    public ResponseEntity<String> unbanIp(@PathVariable String ip) {
        int deletedRows = httpLogService.unbanIp(ip);
        if (deletedRows > 0) {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                    .body("IP: " + ip + " 已成功解封。");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                    .body("未找到需要解封的IP: " + ip);
        }
    }
}