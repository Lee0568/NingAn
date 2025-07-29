package net.thekingofduck.loki.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private IpBanInterceptor ipBanInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ipBanInterceptor)
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns("/api/admin/**"); // 如果有管理界面，排除管理API，避免把自己也封禁了
    }
}