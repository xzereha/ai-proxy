package com.example.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/** Spring Boot application entry point. */
@SpringBootApplication
@EnableCaching
public class AiApplication {

    /** Entry point. */
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
