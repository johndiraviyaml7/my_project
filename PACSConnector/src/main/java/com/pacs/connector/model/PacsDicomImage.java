package com.pacs.connector.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pacs_dicom_image")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PacsDicomImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pacs_device_id", nullable = false)
    private PacsDevice pacsDevice;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_path")
    private String storedPath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "patient_id")
    private String patientId;

    @Column(name = "study_instance_uid")
    private String studyInstanceUid;

    @Column(name = "series_instance_uid")
    private String seriesInstanceUid;

    @Column(name = "sop_instance_uid")
    private String sopInstanceUid;

    @Column(name = "modality")
    private String modality;

    @Column(name = "study_date")
    private String studyDate;

    /**
     * Upload status: "received", "stored", "failed"
     */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "received";

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now();
    }
}
