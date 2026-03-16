package com.pacs.connector.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pacs_device")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacsDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_name", nullable = false, unique = true)
    private String deviceName;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "mqtt_client_id")
    private String mqttClientId;

    @Column(name = "description")
    private String description;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "pacsDevice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PacsConnectivityStatus> connectivityStatuses;

    @OneToMany(mappedBy = "pacsDevice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PacsDicomImage> dicomImages;

    @PrePersist
    public void prePersist() {
        this.registeredAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
