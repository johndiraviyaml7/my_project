package com.quantixmed.dicom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DicomDtos {

    // ── Auth ──────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private String tokenType;
        private String username;
        private String name;
        private long expiresIn;
    }

    // ── PACS Device ───────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeviceDto {
        private UUID id;
        private String name;
        private String serialNumber;
        private String modality;
        private String status;
        private String location;
        private String manufacturer;
        private String model;
        private String ipAddress;
        private Integer port;
        private String aeTitle;
        private String description;
        private Boolean isActive;
        private Long subjectCount;
        private Long instanceCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeviceCreateDto {
        private String name;
        private String serialNumber;
        private String modality;
        private String status;
        private String location;
        private String manufacturer;
        private String model;
        private String ipAddress;
        private Integer port;
        private String aeTitle;
        private String description;
    }

    // ── Subject ───────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SubjectDto {
        private UUID id;
        private UUID deviceId;
        private String deviceName;
        private String subjectId;
        private String collection;
        private String site;
        private String speciesDescription;
        private Boolean isPhantom;
        private String loadStatus;
        private Integer totalStudies;
        private Integer totalSeries;
        private Integer totalInstances;
        private String deidSubjectId;
        private String patientSex;
        private String patientAge;
        private BigDecimal patientWeight;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ── Study (with Orthanc + OHIF viewer URL) ────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StudyDto {
        private UUID id;
        private UUID subjectId;
        private String subjectLabel;
        private String studyInstanceUid;
        private LocalDate studyDate;
        private LocalTime studyTime;
        private String studyDescription;
        private String studyId;
        private String accessionNumber;
        private Integer totalSeries;
        private Integer totalInstances;
        // Orthanc / OHIF
        private String orthancStudyId;
        private String orthancStudyUid;
        private String wadoStudyUrl;
        private String ohifViewerUrl;    // constructed by service
        private Boolean hasViewer;       // true if Orthanc study ID is present
        // De-identified
        private String deidStudyInstanceUid;
        private LocalDate deidStudyDate;
        private String deidAccessionNumber;
        // ── Subject context (for tag table) ───────────────────
        private String subjectCollection;
        private String subjectSite;
        private String subjectSpecies;
        private Boolean subjectIsPhantom;
        private String deidSubjectId;
        // ── Patient context (for tag table) ───────────────────
        private String patientId;
        private String patientName;
        private String patientBirthDate;
        private String patientSex;
        private String patientAge;
        private java.math.BigDecimal patientWeight;
        private java.math.BigDecimal patientSize;
        private String deidPatientId;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ── Series (with Orthanc ID) ──────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SeriesDto {
        private UUID id;
        private UUID studyId;
        private String seriesInstanceUid;
        private Integer seriesNumber;
        private LocalDate seriesDate;
        private String seriesDescription;
        private String modality;
        private String bodyPartExamined;
        private String protocolName;
        private String manufacturer;
        private String manufacturerModel;
        private BigDecimal sliceThickness;
        private BigDecimal pixelSpacingRow;
        private BigDecimal pixelSpacingCol;
        private String patientPosition;
        private Integer imageCount;
        // Orthanc
        private String orthancSeriesId;
        private String deidSeriesInstanceUid;
        private Instant createdAt;
    }

    // ── Instance (with Orthanc ID) ────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InstanceDto {
        private UUID id;
        private UUID seriesId;
        private String sopInstanceUid;
        private String sopClassUid;
        private Integer instanceNumber;
        private LocalDate acquisitionDate;
        private Integer rows;
        private Integer cols;
        private Integer bitsAllocated;
        private String photometricInterp;
        private BigDecimal sliceLocation;
        private Long fileSizeBytes;
        // File paths (raw + de-identified)
        private String filePath;
        private String deidFilePath;
        // Orthanc
        private String orthancInstanceId;
        private String deidSopInstanceUid;
        private Instant createdAt;
    }

    // ── Orthanc status ────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OrthancStatusDto {
        private Boolean available;
        private String url;
        private String dicomWebRoot;
        private String ohifViewerBase;
        private Integer studyCount;
    }
}
