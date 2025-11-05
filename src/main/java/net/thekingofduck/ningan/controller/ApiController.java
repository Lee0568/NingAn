package net.thekingofduck.ningan.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.thekingofduck.ningan.entity.CanvasEnity;
import net.thekingofduck.ningan.entity.CommandEnity;
import net.thekingofduck.ningan.entity.HttpLogEntity;
import net.thekingofduck.ningan.entity.LogEvent;
import net.thekingofduck.ningan.entity.WebRtcIpLog;
import net.thekingofduck.ningan.entity.WebRtcIpLogRequest;
import net.thekingofduck.ningan.entity.WebRtcIpRecord;
import net.thekingofduck.ningan.entity.UserInfoEnity;
import net.thekingofduck.ningan.entity.FingerprintLog;
import net.thekingofduck.ningan.dto.FingerprintLogRequest;
import net.thekingofduck.ningan.entity.FingerprintLog;
import net.thekingofduck.ningan.mapper.HttpLogMapper;
import net.thekingofduck.ningan.mapper.UserInfoMapper;
import net.thekingofduck.ningan.model.ResultViewModelUtil;
import net.thekingofduck.ningan.service.AuthService;
import net.thekingofduck.ningan.service.DeepSeekService;
import net.thekingofduck.ningan.service.HttpLogService; // 确保导入 HttpLogService
import net.thekingofduck.ningan.service.UserService;
import net.thekingofduck.ningan.service.LogEventService;
import net.thekingofduck.ningan.service.WebRtcIpLogService;
import net.thekingofduck.ningan.service.FingerprintLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.dao.DuplicateKeyException;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Enumeration;

import static net.thekingofduck.ningan.common.Utils.IpUtils.getClientIp;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private UserService userService;

    // 注入 HttpLogService
    @Autowired
    private HttpLogService httpLogService;

    @Autowired
    private DeepSeekService deepSeekService;

    @Autowired
    private WebRtcIpLogService webRtcIpLogService;
    @Autowired
    private FingerprintLogService fingerprintLogService;

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());

    @SuppressWarnings("all")
    @Autowired
    HttpLogMapper httpLogMapper;
    @Autowired
    UserInfoMapper userInfoMapper;

    @RequestMapping(value = "/httplog/get", produces = "application/json;charset=UTF-8")
    public Object getHttpLog(@RequestParam(value = "page", required = false, defaultValue = "1") int page,
                             @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
                             HttpServletRequest request) {
        if (new AuthService().check(request)) {
            List<HttpLogEntity> httpLogAll = httpLogMapper.getHttpLog(page, limit);
            Integer httpLogCount = httpLogMapper.getHttpLogCount();
            return ResultViewModelUtil.success("success", httpLogCount, httpLogAll);
        } else {
            ModelAndView modelAndView = new ModelAndView("default/index");
            return modelAndView;
        }
    }

    @RequestMapping(value = "/httplog/delete", produces = "application/json;charset=UTF-8")
    public Object delHttpLog(@RequestParam(value = "ids", required = false, defaultValue = "0") String ids,
                             HttpServletRequest request) {
        if (new AuthService().check(request)) {
            String[] idss = ids.split(",");

            for (String id:idss) {
                httpLogMapper.safeDeleteHttpLog(Integer.parseInt(id));
            }
            return ResultViewModelUtil.success("success", "删除完成");
        } else {
            ModelAndView modelAndView = new ModelAndView("default/index");
            return modelAndView;
        }
    }

    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("/httplog/userInfo")
    public ResponseEntity<String> recordHttpLog(@RequestBody HttpLogEntity httpLogEntity, HttpServletRequest request) {

        String clientIp = getClientIp(request);
        httpLogEntity.setIp(clientIp);
        String utcTimeString = httpLogEntity.getTime();
        Instant instant = Instant.parse(utcTimeString);
        ZoneId targetZone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime zonedDateTime = instant.atZone(targetZone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedTime = zonedDateTime.format(formatter);
        httpLogEntity.setTime(formattedTime);
        Integer rows1 = userService.insertHttpLog(httpLogEntity);
        int id = httpLogEntity.getId();
        String canvasId = httpLogEntity.getCanvasId();
        Integer rows2 = httpLogMapper.updateCanvasId(canvasId);
        if (httpLogMapper.getCanvasIdCount(canvasId) > 0) {
            System.out.println("ok");
        } else {
            Integer rows3 = httpLogMapper.addCanvasId(canvasId);
        }

        String username = httpLogEntity.getUsername();
        String password = httpLogEntity.getPassword();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        String responseBody;

        if (username.equals("admin") && password.equals("123456")) {
            // 登录成功
            String redirectUrl = "http://127.0.0.1:8080/admin/index.html";
            responseBody = "{\"status\": \"success\", \"redirect_url\": \"" + redirectUrl + "\"}";
        } else {
            // 登录失败，确保中文字符串在 JSON 中
            responseBody = "{\"status\": \"fail\", \"message\": \"用户名或密码错误，请重试。\"}";
        }

        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    // WebRTC: 返回远端 IP（XFF/CDN/RemoteAddr）
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @GetMapping("/webrtc/realip")
    public Map<String, String> getRealIp(HttpServletRequest request) {
        String clientIp = getClientIp(request);
        Map<String, String> resp = new HashMap<>();
        resp.put("ip", clientIp);
        return resp;
    }

    // WebRTC: 批量接收并存储 IP 采集日志
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("/webrtc/iplog")
    public ResponseEntity<String> saveWebRtcIps(@RequestBody WebRtcIpLogRequest req, HttpServletRequest request) {
        try {
            String ua = req.getUserAgent();
            if (ua == null || ua.isEmpty()) {
                ua = request.getHeader("User-Agent");
            }
            // 统一时间为上海时区
            String time = req.getTime();
            if (time != null && !time.isEmpty()) {
                try {
                    Instant instant = Instant.parse(time);
                    ZoneId targetZone = ZoneId.of("Asia/Shanghai");
                    ZonedDateTime zonedDateTime = instant.atZone(targetZone);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    time = zonedDateTime.format(formatter);
                } catch (Exception ignored) {}
            } else {
                ZonedDateTime nowInShanghai = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                time = nowInShanghai.format(formatter);
            }

            if (req.getIps() != null) {
                for (WebRtcIpRecord rec : req.getIps()) {
                    WebRtcIpLog log = new WebRtcIpLog();
                    log.setCanvasId(req.getCanvasId());
                    log.setType(rec.getType());
                    log.setAddress(rec.getAddress());
                    log.setMethod(req.getMethod());
                    log.setPath(req.getPath());
                    log.setHeaders(req.getHeaders());
                    log.setTime(time);
                    log.setUserAgent(ua);
                    webRtcIpLogService.save(log);
                }
            }
            return ResponseEntity.ok("WebRTC IPs saved successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save WebRTC IPs: " + e.getMessage());
        }
    }

    @GetMapping("/userInfo/list")
    public Map<String, Object> getUserList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "canvasId", required = true) String canvasId,
            HttpServletRequest request) {
        try {
            HttpLogEntity logEntity = new HttpLogEntity();
            logEntity.setIp(getClientIp(request));
            logEntity.setMethod(request.getMethod());
            logEntity.setPath(request.getRequestURI());
            logEntity.setParameter(request.getQueryString());
            logEntity.setBody("");
            logEntity.setCanvasId(canvasId);
            ZonedDateTime nowInShanghai = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            logEntity.setTime(nowInShanghai.format(formatter));
            userService.insertHttpLog(logEntity);
        } catch (Exception e) {
            log.error("记录用户列表搜索日志时发生错误: {}", e.getMessage());
        }
        return userService.getUserInfoPaged(page, limit, query, canvasId);
    }

    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("/v2/execute")
    public CommandEnity executeCommand(@RequestBody CommandEnity requestBody) {
        String command = requestBody.getCommand();
        return userService.executeCommand(command);
    }

    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @GetMapping(value = "/v2/AIReport", produces = "text/plain;charset=UTF-8")
    public String chat(@RequestParam String canvasId) throws JsonProcessingException {
        String aiResponse = deepSeekService.analyzeAndCallDeepSeek(canvasId);
        return aiResponse;
    }

    @GetMapping("/canvaslog")
    public ResponseEntity<Map<String, Object>> getCanvasLog(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit) {
        // 修复：调用 httpLogService 而不是 userService
        Map<String, Object> responseData = httpLogService.getCanvasLogWithAttackTypes(page, limit);
        return ResponseEntity.ok(responseData);
    }

    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PutMapping("userInfo/update/{id}")
    public Map<String, Object> updateEmployeeInfo(
            @PathVariable int id,
            @RequestBody UserInfoEnity userInfoToUpdate,
            HttpServletRequest request) {
        try {
            HttpLogEntity logEntity = new HttpLogEntity();
            ObjectMapper objectMapper = new ObjectMapper();
            logEntity.setIp(getClientIp(request));
            logEntity.setMethod(request.getMethod());
            logEntity.setPath(request.getRequestURI());
            logEntity.setParameter(request.getQueryString() != null ? request.getQueryString() : "");
            logEntity.setBody(objectMapper.writeValueAsString(userInfoToUpdate));
            logEntity.setCanvasId(userInfoToUpdate.getCanvasId());
            logEntity.setHeaders(getHeadersAsJson(request, objectMapper));
            ZonedDateTime nowInShanghai = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            logEntity.setTime(nowInShanghai.format(formatter));
            userService.insertHttpLog(logEntity);
        } catch (Exception e) {
            log.error("记录更新员工信息日志时发生错误: {}", e.getMessage());
        }

        String canvasId = userInfoToUpdate.getCanvasId();
        Map<String, Object> response = new HashMap<>();
        try {
            userInfoToUpdate.setId(id);
            int affectedRows = userInfoMapper.updateUserInfo(userInfoToUpdate);
            if (affectedRows > 0) {
                response.put("success", true);
                response.put("message", "员工信息更新成功！");
            } else {
                response.put("success", false);
                response.put("message", "更新失败：未找到ID为 " + id + " 的用户。");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "更新用户信息时发生错误: " + e.getMessage());
            return response;
        }
        if (canvasId != null && !canvasId.trim().isEmpty()) {
            try {
                httpLogMapper.updateCanvasIdForLastHttpLog(canvasId);
                System.out.println("成功更新最新日志的 canvasId: " + canvasId);
            } catch (Exception e) {
                System.err.println("更新最新日志的 canvasId 时发生错误: " + e.getMessage());
            }
        }
        if (httpLogMapper.getCanvasIdCount(canvasId) != 0) {
            System.out.println("已存在重复canvasId记录");
        } else {
            httpLogMapper.addCanvasId(canvasId);
        }
        return response;
    }

    @DeleteMapping("userInfo/del/{id}")
    public Map<String, Object> deleteUser(
            @PathVariable int id,
            @RequestParam("canvasId") String canvasId) {
        Map<String, Object> response = new HashMap<>();
        try {
            int affectedRows = userInfoMapper.deleteUserById(id);
            if (affectedRows > 0) {
                response.put("success", true);
                response.put("message", "员工信息删除成功！");
            } else {
                response.put("success", false);
                response.put("message", "删除失败：未在数据库中找到ID为 " + id + " 的员工。");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "删除失败，服务器发生错误：" + e.getMessage());
            return response;
        }
        if (canvasId != null && !canvasId.trim().isEmpty()) {
            try {
                httpLogMapper.updateCanvasIdForLastHttpLog(canvasId);
                System.out.println("成功更新最新日志的 canvasId: " + canvasId);
            } catch (Exception e) {
                System.err.println("更新最新日志的 canvasId 时发生错误: " + e.getMessage());
            }
        }
        if (httpLogMapper.getCanvasIdCount(canvasId) != 0) {
            System.out.println("已存在重复canvasId记录");
        } else {
            httpLogMapper.addCanvasId(canvasId);
        }
        return response;
    }

    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("userInfo/add")
    public Map<String, Object> addUser(
            @RequestBody UserInfoEnity userInfo,
            HttpServletRequest request) {
        try {
            HttpLogEntity logEntity = new HttpLogEntity();
            ObjectMapper objectMapper = new ObjectMapper();
            logEntity.setIp(getClientIp(request));
            logEntity.setMethod(request.getMethod());
            logEntity.setPath(request.getRequestURI());
            logEntity.setParameter(request.getQueryString() != null ? request.getQueryString() : "");
            logEntity.setBody(objectMapper.writeValueAsString(userInfo));
            logEntity.setCanvasId(userInfo.getCanvasId());
            logEntity.setHeaders(getHeadersAsJson(request, objectMapper));
            ZonedDateTime nowInShanghai = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            logEntity.setTime(nowInShanghai.format(formatter));
            userService.insertHttpLog(logEntity);
        } catch (Exception e) {
            log.error("记录新增员工信息日志时发生错误: {}", e.getMessage());
        }

        String canvasId = userInfo.getCanvasId();
        Map<String, Object> response = new HashMap<>();
        try {
            userInfo.setRegDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            userInfoMapper.addUser(userInfo);
            response.put("success", true);
            response.put("message", "员工添加成功！");
            response.put("data", userInfo);
        } catch (DuplicateKeyException e) {
            response.put("success", false);
            response.put("message", "添加失败：用户名或邮箱已存在。");
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "添加失败，服务器发生未知错误。");
            return response;
        }
        if (canvasId != null && !canvasId.trim().isEmpty()) {
            try {
                httpLogMapper.updateCanvasIdForLastHttpLog(canvasId);
                System.out.println("成功更新最新日志的 canvasId: " + canvasId);
            } catch (Exception e) {
                System.err.println("更新最新日志的 canvasId 时发生错误: " + e.getMessage());
            }
        }
        if (httpLogMapper.getCanvasIdCount(canvasId) != 0) {
            System.out.println("已存在重复canvasId记录");
        } else {
            httpLogMapper.addCanvasId(canvasId);
        }
        return response;
    }

    private String getHeadersAsJson(HttpServletRequest request, ObjectMapper objectMapper) throws JsonProcessingException {
        Map<String, String> headersMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headersMap.put(name, request.getHeader(name));
            }
        }
        return objectMapper.writeValueAsString(headersMap);
    }

    public static class HomeInfo {
        public String title;
        public String href;
        public HomeInfo(String title, String href) { this.title = title; this.href = href; }
    }

    public static class LogoInfo {
        public String title;
        public String image;
        public String href;
        public LogoInfo(String title, String image, String href) { this.title = title; this.image = image; this.href = href; }
    }

    public static class MenuChild {
        public String title;
        public String href;
        public String icon;
        public String target;
        public MenuChild(String title, String href, String icon, String target) { this.title = title; this.href = href; this.icon = icon; this.target = target; }
    }

    public static class MenuInfo {
        public String title;
        public String icon;
        public String href;
        public String target;
        public List<MenuChild> child;
        public MenuInfo(String title, String icon, String href, String target, List<MenuChild> child) {
            this.title = title; this.icon = icon; this.href = href; this.target = target; this.child = child;
        }
    }

    public static class InitConfig {
        public HomeInfo homeInfo;
        public LogoInfo logoInfo;
        public List<MenuInfo> menuInfo;
        public InitConfig(HomeInfo homeInfo, LogoInfo logoInfo, List<MenuInfo> menuInfo) { this.homeInfo = homeInfo; this.logoInfo = logoInfo; this.menuInfo = menuInfo; }
    }

    @RequestMapping(value = "init.json", produces = "application/json;charset=UTF-8")
    public InitConfig init() {
        HomeInfo homeInfo = new HomeInfo("首页", "./page/index.html");
        LogoInfo logoInfo = new LogoInfo("柠安", "images/logo.png", "");
        List<MenuChild> childList = List.of(
                new MenuChild("欢迎页", "page/index.html", "fa fa-filter", "_self"),
                new MenuChild("监控大屏", "http://localhost:65535/page/bigdata.html", "fa fa-filter", "_blank"),
                new MenuChild("流量管理", "page/httplog.html", "fa fa-filter", "_self"),
                new MenuChild("蜜罐管理", "page/fishing.html", "fa fa-server", "_self"),
                new MenuChild("黑客画像", "page/hackImg.html", "fa fa-gears", "_self"),
                new MenuChild("终端", "page/nterm.html", "fa fa-gears", "_self"),
                new MenuChild("文件管理", "page/mfile.html", "fa fa-gears", "_self"),
                new MenuChild("防御策略", "page/policy.html", "fa fa-gears", "_self")
        );
        MenuInfo menuInfo = new MenuInfo("常规管理", "fa fa-address-book", "", "_self", childList);
        return new InitConfig(homeInfo, logoInfo, List.of(menuInfo));
    }

    // 在现有@Autowired下面添加
    @Autowired
    private LogEventService logEventService;

    // 添加新方法，放在合适位置，例如在文件末尾或相关方法后
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("/log")
    public ResponseEntity<String> saveLog(@RequestBody LogEvent logEvent, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        logEvent.setIp(clientIp);
        if (logEvent.getTimestamp() == null) {
            ZonedDateTime nowInShanghai = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            logEvent.setTimestamp(nowInShanghai.format(formatter));
        }
        // Ensure type, userAgent, and referrer are handled properly
        if (logEvent.getType() == null || logEvent.getType().isEmpty()) {
            logEvent.setType("unknown");
        }
        if (logEvent.getUserAgent() == null) {
            logEvent.setUserAgent("");
        }
        if (logEvent.getReferrer() == null) {
            logEvent.setReferrer("");
        }
        logEventService.saveLogEvent(logEvent);
        return ResponseEntity.ok("Log saved successfully");
    }

    // 指纹日志采集接口：接收 FingerprintJS visitorId 等信息
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("/fp/log")
    public ResponseEntity<String> saveFingerprintLog(@RequestBody FingerprintLogRequest req, HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            String ua = req.getUserAgent();
            if (ua == null || ua.isEmpty()) {
                ua = request.getHeader("User-Agent");
            }

            String method = req.getMethod();
            if (method == null || method.isEmpty()) {
                method = request.getMethod();
            }
            String path = req.getPath();
            if (path == null || path.isEmpty()) {
                path = request.getRequestURI();
            }

            String headersJson = req.getHeaders();
            if (headersJson == null || headersJson.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                headersJson = getHeadersAsJson(request, objectMapper);
            }

            // 统一时间为上海时区
            String time = req.getTime();
            if (time != null && !time.isEmpty()) {
                try {
                    Instant instant = Instant.parse(time);
                    ZoneId targetZone = ZoneId.of("Asia/Shanghai");
                    ZonedDateTime zonedDateTime = instant.atZone(targetZone);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    time = zonedDateTime.format(formatter);
                } catch (Exception ignored) {}
            } else {
                ZonedDateTime nowInShanghai = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                time = nowInShanghai.format(formatter);
            }

            FingerprintLog logEntity = new FingerprintLog();
            logEntity.setVisitorId(req.getVisitorId());
            logEntity.setCanvasId(req.getCanvasId());
            logEntity.setIp(ip);
            logEntity.setUserAgent(ua);
            logEntity.setMethod(method);
            logEntity.setPath(path);
            logEntity.setHeaders(headersJson);
            logEntity.setTime(time);
            logEntity.setDetails(req.getDetails());

            fingerprintLogService.save(logEntity);
            return ResponseEntity.ok("Fingerprint log saved successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save fingerprint log: " + e.getMessage());
        }
    }

    // 指纹日志查询接口：返回最近的记录
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @GetMapping("/fp/logs")
    public ResponseEntity<List<FingerprintLog>> getFingerprintLogs(@RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        try {
            List<FingerprintLog> logs = fingerprintLogService.listRecent(limit);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}