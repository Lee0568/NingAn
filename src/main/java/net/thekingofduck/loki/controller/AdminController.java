package net.thekingofduck.loki.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import net.thekingofduck.loki.common.Crypto;
import net.thekingofduck.loki.service.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 25/7/28 从数据库拿数据登入
 */


@Controller
public class AdminController {
    
    // 注入AdminUserService
    @Autowired
    private AdminUserService adminUserService;

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());

    @Value("${loki.adminPort}")
    private Integer adminPort;

    @RequestMapping(value = "/${loki.adminPath}")
    public String admincheck2(HttpServletRequest request, HttpServletResponse response) {

        //修改为path校验通过后允许访问function的值 如login css之类的值

        //log.info(request.getRequestURI());

        if (request.getServerPort() == adminPort){

            String method = request.getMethod();

            if ("POST".equals(method)){
                String username = request.getParameter("username");
                String password = request.getParameter("password");
                log.info(String.format("LoginData22233: %s %s", username,password));
                // 使用数据库验证
                if (adminUserService.validateAdminUser(username, password)){
                    try {
                        // 为了保持兼容性，仍然使用原有的加密方式
                        Cookie cookie =new Cookie("token",Crypto.encrypt(username,password));
                        response.addCookie(cookie);
                        log.info(String.format("Logincookie: %s ", cookie));
                        return "pages/admin";
                    }catch (Exception e){
                        log.error(e.toString());
                        return "pages/login";
                    }
                }
            }

            if ("true".equals(request.getParameter("logout"))){
                Cookie cookie =new Cookie("token","delete");
                response.addCookie(cookie);
                response.setStatus(302);
                response.setHeader("Location",request.getRequestURI());
            }

            // 检查是否有有效的会话cookie
            String token = getCookieValue(request, "token");
            if (token != null && !token.equals("delete")) {
                // 这里可以添加对token有效性的验证
                // 为简化实现，我们假设如果有token就认为已登录
                return "pages/admin";
            }
            
            return "pages/login";
        }else {
            return "default/index";
        }
    }
    
    /**
     * 从请求中获取指定名称的Cookie值
     * @param request HTTP请求
     * @param name Cookie名称
     * @return Cookie值，如果未找到则返回null
     */
    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}