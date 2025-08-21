package net.thekingofduck.loki.controller;

import net.thekingofduck.loki.service.IpBanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ip-bans")
@CrossOrigin(origins = "http://127.0.0.1:8080") // 添加此注解以允许来自 8080 端口的跨域请求
public class IpBanController {

    @Autowired
    private IpBanService ipBanService;

    /**
     * 获取所有被封禁的 IP 列表。
     * 假设 IpBanService 有一个方法可以获取所有被封禁的 IP。
     * 如果您的 httplog 表中 isBan=1 对应被封禁，您需要在 IpBanService 中实现一个获取所有 isBan=1 的 IP 的方法。
     */
    @GetMapping
    public ResponseEntity<List<String>> getBannedIps() {
        // 临时模拟数据，实际应从数据库查询
        // 假设 ipBanService.getAllBannedIps() 返回一个 IpBanEntity 列表
        // 然后您可以从中提取 IP 地址
        List<String> bannedIps = List.of("192.168.1.1", "10.0.0.5"); // 替换为实际从服务获取的数据

        // 明确设置 Content-Type 为 application/json;charset=UTF-8
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return new ResponseEntity<>(bannedIps, headers, HttpStatus.OK);
    }

    /**
     * 封禁指定的 IP 地址。
     *
     * @param canvasId 要封禁的 IP 地址
     * @return 响应实体
     */
    @PostMapping("/ban")
    public ResponseEntity<String> banIp(@RequestParam String canvasId) {
        ipBanService.banIp(canvasId);
        String message = "IP " + canvasId + " 已成功封禁.";

        // 明确设置 Content-Type 为 text/plain;charset=UTF-8
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
        return new ResponseEntity<>(message, headers, HttpStatus.OK);
    }

    /**
     * 解封指定的 IP 地址。
     *
     * @param canvasId 要解封的 IP 地址
     * @return 响应实体
     */
    @PostMapping("/unban")
    public ResponseEntity<String> unbanIp(@RequestParam String canvasId) {
        ipBanService.unbanIp(canvasId);
        String message = "IP " + canvasId + " 已成功解封.";

        // 明确设置 Content-Type 为 text/plain;charset=UTF-8
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
        return new ResponseEntity<>(message, headers, HttpStatus.OK);
    }
}