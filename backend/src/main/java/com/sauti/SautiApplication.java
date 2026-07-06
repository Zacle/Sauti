package com.sauti;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SautiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SautiApplication.class, args);
    }
}
