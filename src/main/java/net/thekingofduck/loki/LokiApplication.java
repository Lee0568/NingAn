package net.thekingofduck.loki;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling; // 确保这个 import 存在
import org.springframework.web.client.RestTemplate;

import java.util.TimeZone;

@EnableScheduling
@Configuration
@SpringBootApplication
@MapperScan("net.thekingofduck.loki.mapper")
public class LokiApplication {
    public static void main(String[] args) {
        // --- ↓↓↓ 2. 在 SpringApplication.run 之前添加这行代码 ↓↓↓ ---
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));

        SpringApplication.run(LokiApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}