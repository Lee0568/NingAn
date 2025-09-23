package net.thekingofduck.loki.config;

import net.thekingofduck.loki.interceptor.IpThrottlingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private IpThrottlingInterceptor ipThrottlingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册IP节流拦截器，并让它拦截所有请求 ("/**")
        // 你也可以通过 .excludePathPatterns("/api/some/public/endpoint") 来排除某些不需要拦截的路径
        registry.addInterceptor(ipThrottlingInterceptor).addPathPatterns("/**");
    }
}
