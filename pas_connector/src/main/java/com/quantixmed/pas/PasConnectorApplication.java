package com.quantixmed.pas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PasConnectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(PasConnectorApplication.class, args);
    }
}
