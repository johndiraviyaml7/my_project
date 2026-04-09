package com.quantixmed.dicom.controller;

import com.quantixmed.dicom.dto.DicomDtos.InstanceDto;
import com.quantixmed.dicom.service.InstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Instances")
@CrossOrigin(origins = "*")
public class InstanceController {

    private final InstanceService instanceService;

    @GetMapping("/api/series/{seriesId}/instances")
    @Operation(summary = "List all instances for a series")
    public ResponseEntity<List<InstanceDto>> listBySeries(@PathVariable UUID seriesId) {
        return ResponseEntity.ok(instanceService.listBySeries(seriesId));
    }

    @GetMapping("/api/instances/{id}")
    @Operation(summary = "Get instance by ID")
    public ResponseEntity<InstanceDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(instanceService.getInstance(id));
    }
}
