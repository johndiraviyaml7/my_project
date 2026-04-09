package com.quantixmed.dicom.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "subjects")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private PacsDevice device;

    @Column(name = "subject_id", nullable = false, unique = true)
    private String subjectId;

    private String collection;
    private String site;

    @Column(name = "species_description")
    private String speciesDescription;

    @Builder.Default
    @Column(name = "is_phantom")
    private Boolean isPhantom = false;

    @Column(name = "data_root_path", columnDefinition = "TEXT")
    private String dataRootPath;

    @Builder.Default
    @Column(name = "load_status")
    private String loadStatus = "Pending";

    @Builder.Default
    @Column(name = "total_studies")
    private Integer totalStudies = 0;

    @Builder.Default
    @Column(name = "total_series")
    private Integer totalSeries = 0;

    @Builder.Default
    @Column(name = "total_instances")
    private Integer totalInstances = 0;

    @Column(name = "deid_subject_id")
    private String deidSubjectId;

    @Column(name = "deid_method", columnDefinition = "TEXT")
    private String deidMethod;

    @OneToMany(mappedBy = "subject", fetch = FetchType.LAZY)
    private List<Study> studies;

    @OneToOne(mappedBy = "subject", fetch = FetchType.LAZY)
    private Patient patient;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
