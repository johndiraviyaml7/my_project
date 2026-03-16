package com.pacs.connector.controller;

import com.pacs.connector.model.*;
import com.pacs.connector.mqtt.PacsClientMqttService;
import org.springframework.beans.factory.annotation.Autowired;
import com.pacs.connector.repository.PacsDeviceRepository;
import com.pacs.connector.service.ConnectivityStatusService;
import com.pacs.connector.service.DicomStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PACSConnectorController {

    private final DicomStorageService dicomStorageService;
    private final ConnectivityStatusService connectivityStatusService;
    private final PacsDeviceRepository deviceRepository;
    
    @Autowired(required = false)
    private PacsClientMqttService mqttService;

    // ---- Health / Status ----

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "PACSConnector",
                "mqtt_connected", mqttService != null && mqttService.isConnected(),
                "timestamp", java.time.Instant.now().toString()
        ));
    }

    // ---- DICOM Upload Endpoint (called by EdgeConnector) ----

    @PostMapping("/dicom/upload")
    public ResponseEntity<Map<String, Object>> uploadDicom(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceDevice", defaultValue = "EdgeConnector") String sourceDevice,
            @RequestParam(value = "patientId", defaultValue = "UNKNOWN") String patientId) {

        log.info("Received DICOM upload: {} from device: {}", file.getOriginalFilename(), sourceDevice);

        try {
            PacsDicomImage image = dicomStorageService.storeDicom(file, sourceDevice, patientId);
            return ResponseEntity.ok(Map.of(
                    "status", image.getStatus(),
                    "imageId", image.getId(),
                    "filename", image.getOriginalFilename(),
                    "storedPath", image.getStoredPath() != null ? image.getStoredPath() : ""
            ));
        } catch (Exception e) {
            log.error("DICOM upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // ---- PACS Devices ----

    @GetMapping("/devices")
    public ResponseEntity<List<PacsDevice>> getAllDevices() {
        return ResponseEntity.ok(deviceRepository.findAll());
    }

    @PostMapping("/devices")
    public ResponseEntity<PacsDevice> registerDevice(@RequestBody PacsDevice device) {
        return ResponseEntity.ok(deviceRepository.save(device));
    }

    // ---- Connectivity Status ----

    @GetMapping("/devices/{deviceId}/connectivity")
    public ResponseEntity<List<PacsConnectivityStatus>> getConnectivityHistory(
            @PathVariable UUID deviceId) {
        return ResponseEntity.ok(connectivityStatusService.getStatusHistory(deviceId));
    }

    @GetMapping("/connectivity/lwt")
    public ResponseEntity<List<PacsConnectivityStatus>> getLwtEvents() {
        return ResponseEntity.ok(connectivityStatusService.getLwtEvents());
    }

    // ---- DICOM Images ----

    @GetMapping("/devices/{deviceId}/images")
    public ResponseEntity<List<PacsDicomImage>> getImagesByDevice(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(dicomStorageService.getImagesByDevice(deviceId));
    }

    @GetMapping("/images")
    public ResponseEntity<List<PacsDicomImage>> getAllImages() {
        return ResponseEntity.ok(dicomStorageService.getAllImages());
    }

    @GetMapping("/images/patient/{patientId}")
    public ResponseEntity<List<PacsDicomImage>> getImagesByPatient(@PathVariable String patientId) {
        return ResponseEntity.ok(dicomStorageService.getImagesByPatient(patientId));
    }
}
