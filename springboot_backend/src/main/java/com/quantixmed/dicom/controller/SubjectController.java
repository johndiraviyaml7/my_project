package com.quantixmed.dicom.controller;

import com.quantixmed.dicom.dto.DicomDtos.SubjectDto;
import com.quantixmed.dicom.service.SubjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Subjects")
@CrossOrigin(origins = "*")
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping("/api/devices/{deviceId}/subjects")
    @Operation(summary = "List all subjects for a PACS device")
    public ResponseEntity<List<SubjectDto>> listByDevice(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(subjectService.listByDevice(deviceId));
    }

    @GetMapping("/api/subjects/{id}")
    @Operation(summary = "Get subject by ID")
    public ResponseEntity<SubjectDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(subjectService.getSubject(id));
    }
}
