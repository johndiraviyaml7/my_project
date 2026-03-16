package com.edge.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EdgeConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdgeConnectorApplication.class, args);
    }
}
