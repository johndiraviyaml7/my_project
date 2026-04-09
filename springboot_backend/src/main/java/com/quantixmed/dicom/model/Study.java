package com.quantixmed.dicom.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "studies")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Study {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(name = "study_instance_uid", unique = true, nullable = false)
    private String studyInstanceUid;

    @Column(name = "study_date")
    private LocalDate studyDate;

    @Column(name = "study_time")
    private LocalTime studyTime;

    @Column(name = "study_description", columnDefinition = "TEXT")
    private String studyDescription;

    @Column(name = "study_id")
    private String studyId;

    @Column(name = "accession_number")
    private String accessionNumber;

    @Column(name = "admitting_diagnosis", columnDefinition = "TEXT")
    private String admittingDiagnosis;

    @Builder.Default
    @Column(name = "total_series")
    private Integer totalSeries = 0;

    @Builder.Default
    @Column(name = "total_instances")
    private Integer totalInstances = 0;

    /** Orthanc internal UUID — used to build DICOMweb / OHIF URLs */
    @Column(name = "orthanc_study_id")
    private String orthancStudyId;

    /** De-identified StudyInstanceUID as stored in Orthanc */
    @Column(name = "orthanc_study_uid")
    private String orthancStudyUid;

    /** DICOMweb WADO-RS base URL for this study (used by OHIF) */
    @Column(name = "wado_study_url", columnDefinition = "TEXT")
    private String wadoStudyUrl;

    @Column(name = "deid_study_instance_uid")
    private String deidStudyInstanceUid;

    @Column(name = "deid_study_date")
    private LocalDate deidStudyDate;

    @OneToMany(mappedBy = "study", fetch = FetchType.LAZY)
    private List<Series> seriesList;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
