package com.quantixmed.edge.controller;

import com.quantixmed.edge.dto.EdgeDtos.EdgeStatus;
import com.quantixmed.edge.dto.EdgeDtos.RegisterForm;
import com.quantixmed.edge.dto.EdgeDtos.RegisterResult;
import com.quantixmed.edge.dto.EdgeDtos.UploadResult;
import com.quantixmed.edge.mqtt.EdgeMqttPublisher;
import com.quantixmed.edge.service.PasClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/edge")
@RequiredArgsConstructor
@Slf4j
public class EdgeController {

    private final PasClient pasClient;
    private final EdgeMqttPublisher mqtt;

    @PostMapping("/register")
    public ResponseEntity<RegisterResult> register(@RequestBody RegisterForm form) {
        log.info("Register request from UI: {}", form);
        RegisterResult result = pasClient.register(form);
        if (result.isSuccess()) {
            // After registration succeeds, start the MQTT heartbeat loop
            mqtt.connectAs(form.getSerialNumber());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<EdgeStatus> status() {
        return ResponseEntity.ok(EdgeStatus.builder()
                .registered(mqtt.getSerialNumber() != null)
                .serialNumber(mqtt.getSerialNumber())
                .mqttConnected(mqtt.isConnected())
                .lastHeartbeatAt(mqtt.getLastHeartbeatAt())
                .heartbeatCount(mqtt.getHeartbeatCount())
                .lastError(mqtt.getLastError())
                .build());
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<UploadResult> upload(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "modality", required = false) String modality) {
        String serial = mqtt.getSerialNumber();
        if (serial == null) {
            return ResponseEntity.badRequest().body(UploadResult.builder()
                    .success(false).message("Register the device first.").build());
        }
        try {
            Path tmp = Files.createTempFile("edge-upload-", ".zip");
            try (var in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            mqtt.publishUploadStatus("STARTED", file.getOriginalFilename());
            UploadResult r = pasClient.uploadZip(serial, tmp, modality);
            mqtt.publishUploadStatus(r.isSuccess() ? "COMPLETED" : "FAILED",
                    r.getMessage() == null ? "" : r.getMessage());
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            mqtt.publishUploadStatus("FAILED", e.getMessage());
            return ResponseEntity.ok(UploadResult.builder()
                    .success(false).message(e.getMessage()).build());
        }
    }
}
