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
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "series")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id")
    private Study study;

    @Column(name = "series_instance_uid", unique = true, nullable = false)
    private String seriesInstanceUid;

    @Column(name = "series_number")
    private Integer seriesNumber;

    @Column(name = "series_date")
    private LocalDate seriesDate;

    @Column(name = "series_time")
    private LocalTime seriesTime;

    @Column(name = "series_description", columnDefinition = "TEXT")
    private String seriesDescription;

    private String modality;

    @Column(name = "body_part_examined")
    private String bodyPartExamined;

    @Column(name = "protocol_name")
    private String protocolName;

    private String manufacturer;

    @Column(name = "manufacturer_model")
    private String manufacturerModel;

    @Column(name = "software_versions")
    private String softwareVersions;

    @Column(name = "pixel_spacing_row")
    private BigDecimal pixelSpacingRow;

    @Column(name = "pixel_spacing_col")
    private BigDecimal pixelSpacingCol;

    @Column(name = "slice_thickness")
    private BigDecimal sliceThickness;

    @Column(name = "patient_position")
    private String patientPosition;

    @Builder.Default
    @Column(name = "image_count")
    private Integer imageCount = 0;

    /** Orthanc internal UUID for this series — used by OHIF Viewer */
    @Column(name = "orthanc_series_id")
    private String orthancSeriesId;

    @Column(name = "deid_series_instance_uid")
    private String deidSeriesInstanceUid;

    @Column(name = "deid_series_date")
    private LocalDate deidSeriesDate;

    @OneToMany(mappedBy = "series", fetch = FetchType.LAZY)
    private List<Instance> instances;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
