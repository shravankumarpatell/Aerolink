package com.aerolink.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AeroLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AeroLinkApplication.class, args);
    }
}
