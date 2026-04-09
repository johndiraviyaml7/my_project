package com.quantixmed.dicom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class DicomBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(DicomBackendApplication.class, args);
    }
}
