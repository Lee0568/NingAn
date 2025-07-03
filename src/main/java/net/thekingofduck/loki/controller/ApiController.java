package net.thekingofduck.loki.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.mapper.HttpLogMapper;
import net.thekingofduck.loki.model.ResultViewModelUtil;
import net.thekingofduck.loki.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import javax.print.DocFlavor;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Project: loki
 * Date:2021/1/9 下午11:15
 * @author CoolCat
 * @version 1.0.0
 * Github:https://github.com/TheKingOfDuck
 * When I wirting my code, only God and I know what it does. After a while, only God knows.
 */

@RestController
@RequestMapping("/api")
public class ApiController {

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
        LogoInfo logoInfo = new LogoInfo("LOKI-ADMIN", "images/logo.png", "");
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
