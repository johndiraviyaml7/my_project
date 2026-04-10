package com.quantixmed.edge.controller;

import com.quantixmed.edge.dto.EdgeDtos.EdgeStatus;
import com.quantixmed.edge.dto.EdgeDtos.RegisterForm;
import com.quantixmed.edge.dto.EdgeDtos.RegisterResult;
import com.quantixmed.edge.dto.EdgeDtos.UploadResult;
import com.quantixmed.edge.mqtt.EdgeMqttPublisher;
import com.quantixmed.edge.service.PasClient;
import com.quantixmed.edge.service.RegistrationStore;
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
    private final RegistrationStore registrationStore;

    @PostMapping("/register")
    public ResponseEntity<RegisterResult> register(@RequestBody RegisterForm form) {
        log.info("Register request from UI: {}", form);
        RegisterResult result = pasClient.register(form);
        if (result.isSuccess()) {
            // Persist the form so we survive restarts, then start MQTT
            registrationStore.save(form);
            mqtt.connectAs(form.getSerialNumber());
        }
        return ResponseEntity.ok(result);
    }

    /** Returns the saved registration (if any), so the Swing UI can
     *  pre-populate its form fields on startup. */
    @GetMapping("/saved-registration")
    public ResponseEntity<RegisterForm> savedRegistration() {
        RegisterForm saved = registrationStore.load();
        if (saved == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(saved);
    }

    /** Clears the persisted registration and disconnects MQTT. */
    @PostMapping("/unregister")
    public ResponseEntity<String> unregister() {
        registrationStore.clear();
        mqtt.shutdown();
        return ResponseEntity.ok("cleared");
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
