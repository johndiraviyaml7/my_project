package com.quantixmed.dicom.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patients")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(name = "dicom_patient_id")
    private String dicomPatientId;

    @Column(name = "patient_sex")
    private String patientSex;

    @Column(name = "patient_age")
    private String patientAge;

    @Column(name = "patient_weight")
    private BigDecimal patientWeight;

    @Column(name = "patient_size")
    private BigDecimal patientSize;

    @Column(name = "ethnic_group")
    private String ethnicGroup;

    @Column(name = "patient_identity_removed")
    private String patientIdentityRemoved;

    @Column(name = "deidentification_method", columnDefinition = "TEXT")
    private String deidentificationMethod;

    @Column(name = "deid_patient_id")
    private String deidPatientId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
