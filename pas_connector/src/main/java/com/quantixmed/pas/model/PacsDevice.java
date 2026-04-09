package com.quantixmed.pas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pacs_devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PacsDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "serial_number", unique = true)
    private String serialNumber;

    private String modality;

    private String status;

    /** We store institute_name into the existing `location` column
     *  so we don't need a schema migration for it. */
    private String location;

    private String manufacturer;
    private String model;

    @Column(name = "ip_address")
    private String ipAddress;

    private Integer port;

    @Column(name = "ae_title")
    private String aeTitle;

    private String description;

    @Column(name = "is_active")
    private Boolean isActive;

    /** Added in v5 schema delta — when the last MQTT heartbeat arrived. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null)  status  = "Registered";
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
