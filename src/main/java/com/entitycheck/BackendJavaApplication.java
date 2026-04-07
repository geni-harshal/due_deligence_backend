package com.entitycheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableAsync      // ← ADD THIS
@EnableRetry      // ← ADD THIS
public class BackendJavaApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BackendJavaApplication.class, args);
    }
}
