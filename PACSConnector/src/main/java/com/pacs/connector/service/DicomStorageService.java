package com.pacs.connector.service;

import com.pacs.connector.model.PacsDicomImage;
import com.pacs.connector.model.PacsDevice;
import com.pacs.connector.repository.PacsDicomImageRepository;
import com.pacs.connector.repository.PacsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomStorageService {

    private final PacsDicomImageRepository dicomImageRepository;
    private final PacsDeviceRepository deviceRepository;

    @Value("${dicom.storage.folder:C:/pacs/dicom}")
    private String storageFolder;

    @PostConstruct
    public void init() {
        File dir = new File(storageFolder);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("Created DICOM storage folder: {}", storageFolder);
        }
    }

    @Transactional
    public PacsDicomImage storeDicom(MultipartFile file, String sourceDeviceId, String patientId) {
        // Find or auto-register device
        PacsDevice device = deviceRepository.findByMqttClientId(sourceDeviceId)
                .orElseGet(() -> registerDevice(sourceDeviceId));

        String storedFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String dateFolder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path storagePath = Paths.get(storageFolder, dateFolder);

        PacsDicomImage record = PacsDicomImage.builder()
                .pacsDevice(device)
                .originalFilename(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .patientId(patientId)
                .status("received")
                .build();

        try {
            Files.createDirectories(storagePath);
            Path filePath = storagePath.resolve(storedFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            record.setStoredPath(filePath.toString());
            record.setStatus("stored");
            record.setProcessedAt(LocalDateTime.now());
            log.info("Stored DICOM file: {} -> {}", file.getOriginalFilename(), filePath);

        } catch (IOException e) {
            record.setStatus("failed");
            record.setErrorMessage(e.getMessage());
            log.error("Failed to store DICOM file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
        }

        return dicomImageRepository.save(record);
    }

    @Transactional(readOnly = true)
    public List<PacsDicomImage> getAllImages() {
        return dicomImageRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PacsDicomImage> getImagesByDevice(UUID deviceId) {
        return dicomImageRepository.findByPacsDeviceIdOrderByReceivedAtDesc(deviceId);
    }

    @Transactional(readOnly = true)
    public List<PacsDicomImage> getImagesByPatient(String patientId) {
        return dicomImageRepository.findByPatientId(patientId);
    }

    private PacsDevice registerDevice(String mqttClientId) {
        PacsDevice device = PacsDevice.builder()
                .deviceName(mqttClientId)
                .mqttClientId(mqttClientId)
                .deviceType("EdgeConnector")
                .description("Auto-registered on DICOM upload")
                .active(true)
                .build();
        return deviceRepository.save(device);
    }
}
