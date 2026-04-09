package com.quantixmed.dicom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deid_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeidAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "original_tag")
    private String originalTag;

    @Column(name = "original_keyword")
    private String originalKeyword;

    private String action;

    @Column(name = "standard_reference")
    private String standardReference;

    @Column(name = "processed_at")
    private Instant processedAt;
}
