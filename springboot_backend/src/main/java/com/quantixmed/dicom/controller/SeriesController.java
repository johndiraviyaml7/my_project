package com.quantixmed.dicom.controller;

import com.quantixmed.dicom.dto.DicomDtos.SeriesDto;
import com.quantixmed.dicom.service.SeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Series")
@CrossOrigin(origins = "*")
public class SeriesController {

    private final SeriesService seriesService;

    @GetMapping("/api/studies/{studyId}/series")
    @Operation(summary = "List all series for a study")
    public ResponseEntity<List<SeriesDto>> listByStudy(@PathVariable UUID studyId) {
        return ResponseEntity.ok(seriesService.listByStudy(studyId));
    }

    @GetMapping("/api/series/{id}")
    @Operation(summary = "Get series by ID")
    public ResponseEntity<SeriesDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(seriesService.getSeries(id));
    }
}
