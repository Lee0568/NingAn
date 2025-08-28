package net.thekingofduck.loki.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import net.thekingofduck.loki.entity.CanvasEnity;
import net.thekingofduck.loki.entity.CommandEnity;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import net.thekingofduck.loki.model.ResultViewModelUtil;
import net.thekingofduck.loki.service.AuthService;
import net.thekingofduck.loki.service.DeepSeekService;
import net.thekingofduck.loki.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest; // 导入 HttpServletRequest

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
    public String recordHttpLog(@RequestBody HttpLogEntity httpLogEntity,HttpServletRequest request) {
        // 1. 获取客户端IP地址
        String clientIp = getClientIp(request);
        httpLogEntity.setIp(clientIp); // 将获取到的IP设置到 HttpLogEntity 对象中
        Integer rows1 = userService.insertHttpLog(httpLogEntity);


        int id = httpLogEntity.getId();
        String canvasId = httpLogEntity.getCanvasId();
        Integer rows2 = httpLogMapper.updateCanvasId(canvasId);

        if (httpLogMapper.getCanvasIdCount(canvasId)>0){
            System.out.println("ok");
        }else{
            Integer rows3 = httpLogMapper.addCanvasId(canvasId);
        }

        String username = httpLogEntity.getUsername();
        String password = httpLogEntity.getPassword();
        if(username.equals("admin")&&password.equals("123456")) {
            return "success";
        }

        return "fail";
    }

    @GetMapping("/userInfo/list") // 改为 @GetMapping
    public Map<String, Object> getUserList(
            @RequestParam(value = "page", defaultValue = "1") Integer page, // 从URL查询参数获取page，默认值1
            @RequestParam(value = "limit", defaultValue = "10") Integer limit, // 从URL查询参数获取limit，默认值10
            @RequestParam(value = "query", required = false) String query) { // 从URL查询参数获取query，非必需

        // 调用 Service 层的方法，并将 query 参数传递进去
        return userService.getUserInfoPaged(page, limit, query);
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
// --- 关键修改：添加 produces = "text/plain;charset=UTF-8" ---
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080", "http://127.0.0.1:65535"})
    @GetMapping(value = "/v2/AIReport", produces = "text/plain;charset=UTF-8")
    public String chat(@RequestParam String canvasId) {
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
