package net.thekingofduck.loki.controller;

import net.thekingofduck.loki.service.HttpLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = {"http://127.0.0.1:65535", "http://127.0.0.1:8080", "http://localhost:65535", "http://localhost:8080"})
public class LogAnalysisController {

    @Autowired
    private HttpLogService httpLogService;

    @GetMapping(value = "/basehackerimg", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public String getHackerAnalysisReport(@RequestParam("canvasId") String canvasId) {
        return httpLogService.getHackerInfoAndAttackAnalysis(canvasId);
    }

    @GetMapping(value = "/analyze", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public String getAttackIpList() {
        return httpLogService.getAttackIpList();
    }

    @GetMapping(value = "/recent", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public String getRecentLogs(@RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        return httpLogService.getRecentLogs(limit);
    }

}

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://127.0.0.1:65535", "http://127.0.0.1:8080", "http://localhost:65535", "http://localhost:8080"})
class DashboardController {

    @Autowired
    private HttpLogService httpLogService;

    @GetMapping(value = "/dashboard/stats", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public String getDashboardStats() {
        return httpLogService.getDashboardStats();
    }

    @GetMapping(value = "/attack/types", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public String getAttackTypeStats() {
        return httpLogService.getAttackTypeStats();
    }

    @GetMapping(value = "/attack/trends", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
     public String getAttackTrends() {
         return httpLogService.getAttackTrends();
     }
}