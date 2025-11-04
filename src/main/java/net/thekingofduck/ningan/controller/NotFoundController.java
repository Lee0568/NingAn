package net.thekingofduck.ningan.controller;

import cn.hutool.core.codec.Base64;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import net.thekingofduck.ningan.common.Utils;
import net.thekingofduck.ningan.entity.TemplateEntity;
import net.thekingofduck.ningan.entity.HoneypotService;
import net.thekingofduck.ningan.service.HoneypotServiceRegistry;
import net.thekingofduck.ningan.mapper.HttpLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static net.thekingofduck.ningan.common.Utils.httpServletToBase64;

@Controller
public class NotFoundController implements ErrorController {

    static Log log = LogFactory.get(Thread.currentThread().getStackTrace()[1].getClassName());


    @Autowired
    private TemplateEntity templates;

    @Autowired
    HttpLogMapper httpLogMapper;

    @Autowired
    private HoneypotServiceRegistry registry;


    @RequestMapping(value = {"/error"})
    public Object error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 优先根据注册表中端口解析模板，若未匹配则回退到模板配置
        String currentTemplateName = null;
        try {
            for (HoneypotService svc : registry.list()) {
                if (svc.getPort() != null && svc.getPort() == request.getServerPort()) {
                    currentTemplateName = svc.getTemplate();
                    // 若对应服务已被停止，返回停用页面
                    if (!"running".equalsIgnoreCase(svc.getStatus())) {
                        response.setStatus(503);
                        response.addHeader("X-Honeypot-Status", "stopped");
                        return "offline/index";
                    }
                    break;
                }
            }
        } catch (Exception ignored) { }

        // 回退：从模板配置中按端口查找模板名
        if (currentTemplateName == null) {
            String[] templateNames = templates.getList().keySet().toArray(new String[0]);
            for (String templateName:templateNames) {
                Map template = (Map) templates.getList().get(templateName).get(0).get("maps");
                int templatePort = Integer.parseInt((String) template.get("port"));
                if (templatePort == request.getServerPort()){
                    currentTemplateName = templateName;
                    break;
                }
            }
        }

        if (currentTemplateName == null) currentTemplateName = "default";
        log.info(currentTemplateName);

        // 状态 gating 已在上面按注册表判断；若未命中注册表则不拦截

        //获取当前模板路径
        Map template = (Map) templates.getList().get(currentTemplateName).get(0).get("maps");
        String currentTemplate = String.format("%s",template.get("path")).replaceAll(".html","");

        //设置响应头信息
        int code = Integer.parseInt((String) template.get("code"));
        response.setStatus(code);
        Map headers = (Map)template.get("header");
        for (Object key:headers.keySet()) {
            String headerKey = (String) key;
            String headerValue = (String)headers.get(headerKey);
            response.addHeader(headerKey,headerValue);
        }
        return currentTemplate;
    }


    public String getErrorPath() {
        return null;
    }
}
