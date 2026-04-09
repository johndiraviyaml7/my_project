package com.quantixmed.dicom.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "instances")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Instance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;

    @Column(name = "sop_instance_uid", unique = true, nullable = false)
    private String sopInstanceUid;

    @Column(name = "sop_class_uid")
    private String sopClassUid;

    @Column(name = "instance_number")
    private Integer instanceNumber;

    @Column(name = "acquisition_number")
    private Integer acquisitionNumber;

    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;

    @Column(name = "acquisition_time")
    private LocalTime acquisitionTime;

    @Column(name = "content_date")
    private LocalDate contentDate;

    @Column(name = "content_time")
    private LocalTime contentTime;

    private Integer rows;

    @Column(name = "cols")
    private Integer cols;

    @Column(name = "bits_allocated")
    private Integer bitsAllocated;

    @Column(name = "bits_stored")
    private Integer bitsStored;

    @Column(name = "samples_per_pixel")
    private Integer samplesPerPixel;

    @Column(name = "photometric_interp")
    private String photometricInterp;

    @Column(name = "slice_location")
    private BigDecimal sliceLocation;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Orthanc internal UUID for this SOP instance — used by OHIF Viewer */
    @Column(name = "orthanc_instance_id")
    private String orthancInstanceId;

    @Column(name = "deid_sop_instance_uid")
    private String deidSopInstanceUid;

    @Column(name = "deid_file_path", columnDefinition = "TEXT")
    private String deidFilePath;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
