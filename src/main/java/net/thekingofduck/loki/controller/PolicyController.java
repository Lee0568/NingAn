package net.thekingofduck.loki.controller;

import net.thekingofduck.loki.config.SecurityPolicyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {
    @Autowired
    private SecurityPolicyManager policyManager;

    @PostMapping("/autoban")
    public ResponseEntity<String> saveAutoBanSettings(@RequestBody AutoBanSettingsDto settings) {
        policyManager.updatePolicy(settings.getFrequency(), settings.getDuration());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                .body("自动封禁策略已更新。");
    }
}
