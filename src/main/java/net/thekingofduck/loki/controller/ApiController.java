package net.thekingofduck.loki.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import net.thekingofduck.loki.entity.CanvasEnity;
import net.thekingofduck.loki.entity.CommandEnity;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.entity.UserInfoEnity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import net.thekingofduck.loki.mapper.UserInfoMapper;
import net.thekingofduck.loki.model.ResultViewModelUtil;
import net.thekingofduck.loki.service.AuthService;
import net.thekingofduck.loki.service.DeepSeekService;
import net.thekingofduck.loki.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest; // 导入 HttpServletRequest
import java.text.SimpleDateFormat;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.thekingofduck.loki.common.Utils.IpUtils.getClientIp;


@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private UserService userService;

    @Autowired
    private DeepSeekService deepSeekService;

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());

    @SuppressWarnings("all")
    @Autowired
    HttpLogMapper httpLogMapper;
    @Autowired
    UserInfoMapper userInfoMapper;


    @RequestMapping(value="/httplog/get", produces="application/json;charset=UTF-8")
    public Object getHttpLog(@RequestParam(value = "page",required = false,defaultValue = "1") int page,
                             @RequestParam(value = "limit",required = false,defaultValue = "10") int limit,
                             HttpServletRequest request){

        //log.info(request.getRequestURI());

        if (new AuthService().check(request)){
            List<HttpLogEntity> httpLogAll = httpLogMapper.getHttpLog(page,limit);
            Integer httpLogCount = httpLogMapper.getHttpLogCount();
            return ResultViewModelUtil.success("success",httpLogCount,httpLogAll);
        }else {
            ModelAndView modelAndView= new ModelAndView("default/index");
            return modelAndView;
        }
    }

    @RequestMapping(value="/httplog/delete", produces="application/json;charset=UTF-8")
    public Object delHttpLog(@RequestParam(value = "ids",required = false,defaultValue = "0") String ids,
                             HttpServletRequest request){

        //log.info(request.getRequestURI());

        if (new AuthService().check(request)){

            String[] idss = ids.split(",");

            for (String id:idss) {
                httpLogMapper.deleteHttpLogById(Integer.parseInt(id));
            }

            return ResultViewModelUtil.success("success","删除完成");
        }else {
            ModelAndView modelAndView= new ModelAndView("default/index");
            return modelAndView;
        }
    }

    /**
     *
     * 该接口实际功能为记录攻击者信息并插入数据库，因为接口名称可查，故以userInfo命名，防止攻击者识别出蜜罐
     */
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("/httplog/userInfo")
    public String recordHttpLog(@RequestBody HttpLogEntity httpLogEntity, HttpServletRequest request) {
        // 1. 获取客户端IP地址
        String clientIp = getClientIp(request);
        httpLogEntity.setIp(clientIp); // 将获取到的IP设置到 HttpLogEntity 对象中

        String utcTimeString = httpLogEntity.getTime();

        // 1. 解析UTC时间字符串为一个时间点 (Instant)
        Instant instant = Instant.parse(utcTimeString);

        // 2. 定义目标时区 (这里使用 "Asia/Shanghai" 代表 UTC+8)
        ZoneId targetZone = ZoneId.of("Asia/Shanghai");

        // 3. 将UTC时间点转换为目标时区的时间
        ZonedDateTime zonedDateTime = instant.atZone(targetZone);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 5. 将时间格式化为最终的字符串
        String formattedTime = zonedDateTime.format(formatter);

        // 6. 将格式化后的时间设置回实体对象中，准备存入数据库
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
        if (username.equals("admin") && password.equals("123456")) {
            return "success";
        }

        return "fail";
    }

    @GetMapping("/userInfo/list") // 改为 @GetMapping
    public Map<String, Object> getUserList(
            @RequestParam(value = "page", defaultValue = "1") Integer page, // 从URL查询参数获取page，默认值1
            @RequestParam(value = "limit", defaultValue = "10") Integer limit, // 从URL查询参数获取limit，默认值10
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "canvasId", required = true) String canvasId) { // 从URL查询参数获取query，非必需

        // 调用 Service 层的方法，并将 query 参数传递进去
        return userService.getUserInfoPaged(page, limit, query, canvasId);
    }

    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("/v2/execute")
    public CommandEnity executeCommand(@RequestBody CommandEnity requestBody) {
        // 从请求体（反序列化后的CommandEnity对象）中获取command字段的值
        String command = requestBody.getCommand();

        // 调用 CommandExc 服务处理命令并获取模拟结果
        return userService.executeCommand(command);
    }

    /**
     * dick控制层
     * @param canvasId
     * @return
     */
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @GetMapping(value = "/v2/AIReport", produces = "text/plain;charset=UTF-8")
    public String chat(@RequestParam String canvasId) throws JsonProcessingException {
        // 调用 DeepSeekService 获取 DeepSeek API 的解析后的中文回复
        String aiResponse = deepSeekService.analyzeAndCallDeepSeek(canvasId);
        return aiResponse;
    }

    @GetMapping("/canvaslog")
    public ResponseEntity<Map<String, Object>> getCanvasLog(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit) {

        // 查询总记录数
        int totalItems = httpLogMapper.countTotalCanvasLogs();

        // 计算总页数
        int totalPages = (int) Math.ceil((double) totalItems / limit);

        // 查询分页后的数据
        List<CanvasEnity> items = httpLogMapper.selectAllCanvaslogs(page, limit);

        // 封装返回结果
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("items", items);
        responseData.put("totalCount", totalItems);
        responseData.put("totalPages", totalPages);
        responseData.put("currentPage", page);

        // 返回 JSON 格式的响应
        return ResponseEntity.ok(responseData);
    }

    /**
     * 根据ID更新员工信息
     * @param id  从URL路径中获取的要修改的员工ID
     * @param userInfoToUpdate 从请求体中获取的包含更新信息的JSON对象
     * @return 返回包含操作结果的JSON对象
     */
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PutMapping("userInfo/update/{id}")
    public Map<String, Object> updateEmployeeInfo(
            @PathVariable int id,
            @RequestBody UserInfoEnity userInfoToUpdate) {

        // 从传入的请求体对象中获取 canvasId
        String canvasId = userInfoToUpdate.getCanvasId();

        Map<String, Object> response = new HashMap<>();
        try {
            // 核心业务：更新用户信息
            userInfoToUpdate.setId(id); // 确保ID被设置
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
            // 建议在这里就返回，避免后续逻辑在主业务失败时还继续执行
            return response;
        }

        // 1. 更新最新一条日志的 canvasId 字段
        if (canvasId != null && !canvasId.trim().isEmpty()) {
            try {
                httpLogMapper.updateCanvasIdForLastHttpLog(canvasId);
                System.out.println("成功更新最新日志的 canvasId: " + canvasId);
            } catch (Exception e) {
                // 在实际项目中，这里应该使用日志框架记录错误
                System.err.println("更新最新日志的 canvasId 时发生错误: " + e.getMessage());
            }
        }

        // 2. 检查 canvasId 是否重复，不重复则添加
        if (httpLogMapper.getCanvasIdCount(canvasId) != 0) {
            System.out.println("已存在重复canvasId记录");
        } else {
            httpLogMapper.addCanvasId(canvasId);
        }

        return response;
    }

    /**
     * 根据ID删除员工信息
     * @param id 从URL路径中动态获取的员工ID
     * @return 返回一个包含操作结果的JSON对象
     */
    @DeleteMapping("userInfo/del/{id}")
    public Map<String, Object> deleteUser(
            @PathVariable int id,
            @RequestParam("canvasId") String canvasId) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 核心业务：删除用户
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

        // 附属业务：处理 canvasId (这部分逻辑保持不变)
        // 1. 更新最新一条日志的 canvasId 字段
        if (canvasId != null && !canvasId.trim().isEmpty()) {
            try {
                httpLogMapper.updateCanvasIdForLastHttpLog(canvasId);
                System.out.println("成功更新最新日志的 canvasId: " + canvasId);
            } catch (Exception e) {
                System.err.println("更新最新日志的 canvasId 时发生错误: " + e.getMessage());
            }
        }

        // 2. 检查 canvasId 是否重复，不重复则添加
        if (httpLogMapper.getCanvasIdCount(canvasId) != 0) {
            System.out.println("已存在重复canvasId记录");
        } else {
            httpLogMapper.addCanvasId(canvasId);
        }

        return response;
    }

    /**
     * 新增员工信息
     * @param userInfo
     * @return
     */
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @PostMapping("userInfo/add")
    public Map<String, Object> addUser(@RequestBody UserInfoEnity userInfo) {
        // 从传入的请求体对象中获取 canvasId
        String canvasId = userInfo.getCanvasId();

        Map<String, Object> response = new HashMap<>();

        try {
            // 核心业务：新增员工
            userInfo.setRegDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            userInfoMapper.addUser(userInfo);

            response.put("success", true);
            response.put("message", "员工添加成功！");
            response.put("data", userInfo); // 返回包含新生成ID的用户信息

        } catch (DuplicateKeyException e) {
            response.put("success", false);
            response.put("message", "添加失败：用户名或邮箱已存在。");
            // 添加失败时直接返回，不执行后续 canvasId 操作
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "添加失败，服务器发生未知错误。");
            // 添加失败时直接返回
            return response;
        }

        // 1. 更新最新一条日志的 canvasId 字段
        if (canvasId != null && !canvasId.trim().isEmpty()) {
            try {
                httpLogMapper.updateCanvasIdForLastHttpLog(canvasId);
                System.out.println("成功更新最新日志的 canvasId: " + canvasId);
            } catch (Exception e) {
                System.err.println("更新最新日志的 canvasId 时发生错误: " + e.getMessage());
            }
        }

        // 2. 检查 canvasId 是否重复，不重复则添加
        if (httpLogMapper.getCanvasIdCount(canvasId) != 0) {
            System.out.println("已存在重复canvasId记录");
        } else {
            httpLogMapper.addCanvasId(canvasId);
        }

        return response;
    }

    public static class HomeInfo {
        public String title;
        public String href;
        public HomeInfo(String title, String href) {
            this.title = title;
            this.href = href;
        }
    }

    public static class LogoInfo {
        public String title;
        public String image;
        public String href;
        public LogoInfo(String title, String image, String href) {
            this.title = title;
            this.image = image;
            this.href = href;
        }
    }

    public static class MenuChild {
        public String title;
        public String href;
        public String icon;
        public String target;
        public MenuChild(String title, String href, String icon, String target) {
            this.title = title;
            this.href = href;
            this.icon = icon;
            this.target = target;
        }
    }

    public static class MenuInfo {
        public String title;
        public String icon;
        public String href;
        public String target;
        public List<MenuChild> child;
        public MenuInfo(String title, String icon, String href, String target, List<MenuChild> child) {
            this.title = title;
            this.icon = icon;
            this.href = href;
            this.target = target;
            this.child = child;
        }
    }

    public static class InitConfig {
        public HomeInfo homeInfo;
        public LogoInfo logoInfo;
        public List<MenuInfo> menuInfo;
        public InitConfig(HomeInfo homeInfo, LogoInfo logoInfo, List<MenuInfo> menuInfo) {
            this.homeInfo = homeInfo;
            this.logoInfo = logoInfo;
            this.menuInfo = menuInfo;
        }
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
                new MenuChild("智能AI", "page/AI.html", "fa fa-gears", "_self")

        );
        MenuInfo menuInfo = new MenuInfo("常规管理", "fa fa-address-book", "", "_self", childList);
        return new InitConfig(homeInfo, logoInfo, List.of(menuInfo));
    }


}
