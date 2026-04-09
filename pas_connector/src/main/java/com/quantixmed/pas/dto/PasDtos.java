package com.quantixmed.pas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

public class PasDtos {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank private String serialNumber;
        @NotBlank private String deviceName;
        @NotBlank private String modality;
        @NotBlank private String instituteName;
        private String manufacturer;
        private String model;
        private String aeTitle;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RegisterResponse {
        private UUID id;
        private String serialNumber;
        private String deviceName;
        private String status;
        private String message;
        private Instant registeredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeviceStatusDto {
        private UUID id;
        private String serialNumber;
        private String name;
        private String modality;
        private String instituteName;
        private String status;             // Connected | Disconnected | Registered
        private Instant lastSeenAt;
        private Long secondsSinceLastSeen;
        private Boolean isLive;             // heartbeat within timeout window
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusEventDto {
        private Long id;
        private UUID deviceId;
        private String serialNumber;
        private String eventType;
        private String topic;
        private String payload;
        private Instant occurredAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadResponse {
        private String serialNumber;
        private String filename;
        private long bytes;
        private String storedAt;
        private Instant receivedAt;
        private String status;
    }
}
