package com.motionecosystem.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.motionecosystem")
@EnableScheduling
public class MotionEcosystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotionEcosystemApplication.class, args);
    }
}
