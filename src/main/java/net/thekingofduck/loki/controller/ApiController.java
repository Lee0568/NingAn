package net.thekingofduck.loki.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.entity.UserInfoEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import net.thekingofduck.loki.model.ResultViewModelUtil;
import net.thekingofduck.loki.service.AuthService;
import net.thekingofduck.loki.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest; // 导入 HttpServletRequest

import javax.print.DocFlavor;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static net.thekingofduck.loki.common.Utils.IpUtils.getClientIp;


@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private UserService userService;

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());

    @SuppressWarnings("all")
    @Autowired
    HttpLogMapper httpLogMapper;

    @RequestMapping(value="httplog/get", produces="application/json;charset=UTF-8")
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

    @RequestMapping(value="httplog/delete", produces="application/json;charset=UTF-8")
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
    @CrossOrigin(origins = {"http://127.0.0.1:8090", "http://127.0.0.1:8080"})

    @PostMapping("/httplog/userInfo")
    public String recordHttpLog(@RequestBody HttpLogEntity httpLogEntity,HttpServletRequest request) {
        // 1. 获取客户端IP地址
        String clientIp = getClientIp(request);
        httpLogEntity.setIp(clientIp); // 将获取到的IP设置到 HttpLogEntity 对象中
        Integer rows = userService.insertHttpLog(httpLogEntity);
        String username = httpLogEntity.getUsername();
        String password = httpLogEntity.getPassword();
        if (username == null || password == null) {
            return "fail";
        }
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
                new MenuChild("流量管理", "page/httplog.html", "fa fa-filter", "_self"),
                new MenuChild("钓鱼管理", "page/fishing.html", "fa fa-anchor", "_self"),
                new MenuChild("系统设置", "page/setting.html", "fa fa-gears", "_self"),
                new MenuChild("终端", "page/nterm.html", "fa fa-gears", "_self")
        );
        MenuInfo menuInfo = new MenuInfo("常规管理", "fa fa-address-book", "", "_self", childList);
        return new InitConfig(homeInfo, logoInfo, List.of(menuInfo));
    }
// 为什么要这样
//    @RequestMapping(value = "init.json")
//    public Object init() {
//        return "{\n" +
//                "  \"homeInfo\": {\n" +
//                "    \"title\": \"首页\",\n" +
//                "    \"href\": \"./page/index.html\"\n" +
//                "  },\n" +
//                "  \"logoInfo\": {\n" +
//                "    \"title\": \"LOKI-ADMIN\",\n" +
//                "    \"image\": \"images/logo.png\",\n" +
//                "    \"href\": \"\"\n" +
//                "  },\n" +
//                "  \"menuInfo\": [\n" +
//                "    {\n" +
//                "      \"title\": \"常规管理\",\n" +
//                "      \"icon\": \"fa fa-address-book\",\n" +
//                "      \"href\": \"\",\n" +
//                "      \"target\": \"_self\",\n" +
//                "      \"child\": [\n" +
//                "        {\n" +
//                "          \"title\": \"流量管理\",\n" +
//                "          \"href\": \"page/httplog.html\",\n" +
//                "          \"icon\": \"fa fa-filter\",\n" +
//                "          \"target\": \"_self\"\n" +
//                "        },\n" +
//                "        {\n" +
//                "          \"title\": \"钓鱼管理\",\n" +
//                "          \"href\": \"page/fishing.html\",\n" +
//                "          \"icon\": \"fa fa-anchor\",\n" +
//                "          \"target\": \"_self\"\n" +
//                "        },\n" +
//                "        {\n" +
//                "          \"title\": \"系统设置\",\n" +
//                "          \"href\": \"page/setting.html\",\n" +
//                "          \"icon\": \"fa fa-gears\",\n" +
//                "          \"target\": \"_self\"\n" +
//                "        }\n" +
//                "      ]\n" +
//                "    }\n" +
//                "  ]\n" +
//                "}";
//    }
}
