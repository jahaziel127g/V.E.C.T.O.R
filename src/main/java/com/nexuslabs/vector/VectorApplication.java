package com.nexuslabs.vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VectorApplication.class, args);
    }
}