package com.motionecosystem.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.motionecosystem")
public class MotionEcosystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotionEcosystemApplication.class, args);
    }
}
