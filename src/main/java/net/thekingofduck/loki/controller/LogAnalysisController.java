package net.thekingofduck.loki.controller;

import net.thekingofduck.loki.service.HttpLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam; // 导入 RequestParam
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = {"http://127.0.0.1:65535", "http://127.0.0.1:8080"})
public class LogAnalysisController {

    @Autowired
    private HttpLogService httpLogService;

    @GetMapping(value = "/basehackerimg", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public String getHackerAnalysisReport(@RequestParam("canvasId") String canvasId) {
        return httpLogService.getHackerInfoAndAttackAnalysis(canvasId);
    }
}