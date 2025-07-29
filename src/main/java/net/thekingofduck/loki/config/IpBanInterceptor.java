package net.thekingofduck.loki.config;

import net.thekingofduck.loki.service.IpBanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class IpBanInterceptor implements HandlerInterceptor {

    @Autowired
    private IpBanService ipBanService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String clientIp = request.getRemoteAddr(); // 获取客户端IP地址

        // 也可以考虑从X-Forwarded-For等HTTP头获取真实IP，尤其是在有代理或负载均衡时
        // String clientIp = getClientIpAddress(request);

        if (ipBanService.isIpBanned(clientIp)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 设置HTTP状态码为403 Forbidden
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"检测到您的访问可能会对网站造成伤害，您已被禁止访问本网站。\"}");
            return false; // 中断请求
        }
        return true; // 允许请求继续
    }

    // 辅助方法：更可靠地获取客户端IP地址 (考虑代理)
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader != null && !xForwardedForHeader.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedForHeader)) {
            // 通常第一个IP是真实客户端IP
            int commaIndex = xForwardedForHeader.indexOf(',');
            if (commaIndex != -1) {
                return xForwardedForHeader.substring(0, commaIndex).trim();
            }
            return xForwardedForHeader.trim();
        }
        return request.getRemoteAddr();
    }
}