package com.quantixmed.edge.dto;

import lombok.*;

import java.time.Instant;

public class EdgeDtos {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RegisterForm {
        private String serialNumber;
        private String deviceName;
        private String modality;
        private String instituteName;
        private String manufacturer;
        private String model;
        private String aeTitle;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RegisterResult {
        private boolean success;
        private String message;
        private String serialNumber;
        private String deviceId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EdgeStatus {
        private boolean registered;
        private String serialNumber;
        private boolean mqttConnected;
        private Instant lastHeartbeatAt;
        private Long heartbeatCount;
        private String lastError;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadResult {
        private boolean success;
        private String filename;
        private long bytes;
        private String message;
    }
}
