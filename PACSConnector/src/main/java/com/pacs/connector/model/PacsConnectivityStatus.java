package com.pacs.connector.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pacs_connectivity_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacsConnectivityStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pacs_device_id", nullable = false)
    private PacsDevice pacsDevice;

    /**
     * Status values: "connected", "disconnected"
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Source: "mqtt_status" for normal connectivity, "lwt" for ungraceful disconnect
     */
    @Column(name = "message_source", nullable = false)
    private String messageSource;

    /**
     * Reason for disconnect (e.g., "ungraceful_termination", "manual_shutdown")
     */
    @Column(name = "disconnect_reason")
    private String disconnectReason;

    @Column(name = "mqtt_topic")
    private String mqttTopic;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now();
    }
}
