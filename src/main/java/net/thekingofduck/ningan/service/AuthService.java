package net.thekingofduck.ningan.service;

import net.thekingofduck.ningan.common.Crypto;
import net.thekingofduck.ningan.mapper.AdminUserMapper;
import net.thekingofduck.ningan.service.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;


public class AuthService {

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());

    public static String username;
    public static String password;

    // 注入AdminUserService
    private static AdminUserService adminUserService;
    
    @Autowired
    public void setAdminUserService(AdminUserService adminUserService) {
        AuthService.adminUserService = adminUserService;
    }

    public static String getUsername() {
        return username;
    }

    public static void setUsername(String username) {
        AuthService.username = username;
    }

    public static String getPassword() {
        return password;
    }

    public static void setPassword(String password) {
        AuthService.password = password;
    }

    public AuthService() {
        try {
            File exconfig = new File(String.format("%s/application.yml",System.getProperty("user.dir")));
            File inconfig = new File(this.getClass().getClassLoader().getResource("application.yml").getPath());
            InputStream in;
            if (exconfig.exists()){
                in = new FileInputStream(exconfig);
            }else {
                in = new FileInputStream(inconfig);
            }
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.loadAs(in, Map.class);
            setUsername(((Map<String, Object>) map.get("ningan")).get("username").toString().trim());
            setPassword(((Map<String, Object>) map.get("ningan")).get("password").toString().trim());
        }catch (Exception e){
            log.error(e);
        }
    }

    public String getCookies(HttpServletRequest request){
        Cookie[] cookies =  request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if("token".equals(cookie.getName())){
                    return cookie.getValue();
                }
            }
        }
        return  null;
    }

    public boolean check(HttpServletRequest request){

        try {
            String token = getCookies(request);
            //log.info("GetToken: " + token);
            if (Crypto.decrypt(token, getPassword()).equals(getUsername())){
                return true;
            }

            return false;
        }catch (Exception e){
            return false;
        }
    }
    
    /**
     * 验证管理员用户凭据（使用数据库）
     * @param inputUsername 输入的用户名
     * @param inputPassword 输入的密码
     * @return 验证是否成功
     */
    public static boolean validateAdminCredentials(String inputUsername, String inputPassword) {
        // 如果AdminUserService可用，使用数据库验证
        if (adminUserService != null) {
            return adminUserService.validateAdminUser(inputUsername, inputPassword);
        }
        // 否则使用原来的配置文件验证方式
        else {
            return inputUsername.equals(getUsername()) && inputPassword.equals(getPassword());
        }
    }




    public static void main(String[] args) {
        System.out.println(System.getProperty("user.dir"));
    }
}