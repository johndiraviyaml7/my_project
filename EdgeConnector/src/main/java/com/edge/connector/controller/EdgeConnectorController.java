package com.edge.connector.controller;

import com.edge.connector.mqtt.MqttService;
import com.edge.connector.service.DicomUploadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



   
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EdgeConnectorController {

    private final MqttService mqttService;
    private final DicomUploadService dicomUploadService;

    private static final Logger log = LoggerFactory.getLogger(EdgeConnectorController.class);

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "EdgeConnector",
                "mqtt_connected", mqttService.isConnected(),
                "timestamp", java.time.Instant.now().toString()
        ));
    }

    @PostMapping("/mqtt/publish")
    public ResponseEntity<Map<String, String>> publishMessage(
            @RequestParam String topic,
            @RequestBody String payload) {
        mqttService.publish(topic, payload, false);
        return ResponseEntity.ok(Map.of("status", "published", "topic", topic));
    }

    @PostMapping("/mqtt/subscribe")
    public ResponseEntity<Map<String, String>> subscribeTopic(@RequestParam String topic) {
        mqttService.subscribe(topic);
        return ResponseEntity.ok(Map.of("status", "subscribed", "topic", topic));
    }

    @PostMapping("/dicom/upload")
    public ResponseEntity<Map<String, String>> uploadDicom(@RequestParam("file") MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("dicom_", ".dcm");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            dicomUploadService.processFile(tempFile.toFile());
            return ResponseEntity.ok(Map.of("status", "queued", "filename", file.getOriginalFilename()));
        } catch (Exception e) {
            log.error("Failed to queue DICOM file: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/connectivity/update")
    public ResponseEntity<Map<String, String>> updateConnectivity(@RequestParam String status) {
        mqttService.publishConnectivityStatus(status);
        return ResponseEntity.ok(Map.of("status", "published", "connectivity", status));
    }
}
