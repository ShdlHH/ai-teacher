package com.example.aiteacher;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.example.aiteacher.mapper")
public class AiTeacherApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTeacherApplication.class, args);
    }
}
