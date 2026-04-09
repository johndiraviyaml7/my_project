package com.quantixmed.pas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pacs_status_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @PrePersist
    void onCreate() { if (occurredAt == null) occurredAt = Instant.now(); }
}
