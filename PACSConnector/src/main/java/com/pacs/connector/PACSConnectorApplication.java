package com.pacs.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PACSConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PACSConnectorApplication.class, args);
    }
}
