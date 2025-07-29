package net.thekingofduck.loki;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;



@Configuration
@SpringBootApplication
@MapperScan("net.thekingofduck.loki.mapper")
public class LokiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LokiApplication.class, args);
    }
}
