package com.quantixmed.pas.controller;

import com.quantixmed.pas.dto.PasDtos.UploadResponse;
import com.quantixmed.pas.service.StatusEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Receives a PACS DICOM study as a zip file, uploaded by an Edge Connector
 * after it publishes phase=STARTED on pacs/{serial}/upload-status.
 *
 * On success we write an UPLOAD_COMPLETED status event ourselves (in
 * addition to the one the Edge publishes) so the dashboard sees the
 * server-confirmed completion even if the Edge crashes right after upload.
 */
@RestController
@RequestMapping("/api/pas")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final StatusEventService eventService;

    @Value("${pas.upload.dir}")
    private String uploadDir;

    @PostMapping(value = "/upload/{serialNumber}", consumes = "multipart/form-data")
    public ResponseEntity<UploadResponse> upload(@PathVariable String serialNumber,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "modality", required = false) String modality) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(UploadResponse.builder().status("EMPTY").build());
        }

        // Sanitise filename
        String orig = file.getOriginalFilename();
        String safe = orig == null ? "upload.zip"
                : orig.replaceAll("[^A-Za-z0-9._-]", "_");
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        String filename = serialNumber + "_" + ts + "_" + safe;

        Path dir = Paths.get(uploadDir, serialNumber);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename);

        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        long bytes = Files.size(target);
        log.info("Upload received from {}: {} ({} bytes) -> {}",
                serialNumber, orig, bytes, target);

        eventService.record(serialNumber, "UPLOAD_COMPLETED",
                "api/pas/upload",
                "{\"filename\":\"" + safe + "\",\"bytes\":" + bytes
                        + (modality != null ? ",\"modality\":\"" + modality + "\"" : "")
                        + "}");

        return ResponseEntity.ok(UploadResponse.builder()
                .serialNumber(serialNumber)
                .filename(filename)
                .bytes(bytes)
                .storedAt(target.toString())
                .receivedAt(Instant.now())
                .status("OK")
                .build());
    }
}
