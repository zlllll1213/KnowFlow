package com.knowflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.knowflow.mapper")
public class KnowFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowFlowApplication.class, args);
    }
}
