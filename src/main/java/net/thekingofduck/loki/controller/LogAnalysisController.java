package net.thekingofduck.loki.controller;

import net.thekingofduck.loki.service.HttpLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType; // 导入 MediaType 类

@RestController
@RequestMapping("/api/logs")
public class LogAnalysisController {

    @Autowired
    private HttpLogService httpLogService;

    @GetMapping(value = "/analyze", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public String getAnalyzedLogs() {
        return httpLogService.analyzeLogsAndDetectAttacks();
    }
}