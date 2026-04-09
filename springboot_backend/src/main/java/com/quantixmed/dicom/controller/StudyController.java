package com.quantixmed.dicom.controller;

import com.quantixmed.dicom.dto.DicomDtos.StudyDto;
import com.quantixmed.dicom.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Studies")
@CrossOrigin(origins = "*")
public class StudyController {

    private final StudyService studyService;

    @GetMapping("/api/subjects/{subjectId}/studies")
    @Operation(summary = "List all studies for a subject")
    public ResponseEntity<List<StudyDto>> listBySubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(studyService.listBySubject(subjectId));
    }

    @GetMapping("/api/studies/{id}")
    @Operation(summary = "Get study by ID")
    public ResponseEntity<StudyDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(studyService.getStudy(id));
    }
}
