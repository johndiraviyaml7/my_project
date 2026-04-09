package com.quantixmed.dicom.service;

import com.quantixmed.dicom.dto.DicomDtos.SubjectDto;
import com.quantixmed.dicom.model.Patient;
import com.quantixmed.dicom.model.Subject;
import com.quantixmed.dicom.repository.PatientRepository;
import com.quantixmed.dicom.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SubjectService {

    private final SubjectRepository subjectRepo;
    private final PatientRepository patientRepo;

    public List<SubjectDto> listByDevice(UUID deviceId) {
        return subjectRepo.findByDeviceIdOrderByCreatedAtDesc(deviceId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public SubjectDto getSubject(UUID id) {
        Subject s = subjectRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found: " + id));
        return toDto(s);
    }

    private SubjectDto toDto(Subject s) {
        Patient p = patientRepo.findBySubjectId(s.getId()).orElse(null);
        return SubjectDto.builder()
                .id(s.getId())
                .deviceId(s.getDevice() != null ? s.getDevice().getId() : null)
                .deviceName(s.getDevice() != null ? s.getDevice().getName() : null)
                .subjectId(s.getSubjectId())
                .collection(s.getCollection())
                .site(s.getSite())
                .speciesDescription(s.getSpeciesDescription())
                .isPhantom(s.getIsPhantom())
                .loadStatus(s.getLoadStatus())
                .totalStudies(s.getTotalStudies())
                .totalSeries(s.getTotalSeries())
                .totalInstances(s.getTotalInstances())
                .deidSubjectId(s.getDeidSubjectId())
                .patientSex(p != null ? p.getPatientSex() : null)
                .patientAge(p != null ? p.getPatientAge() : null)
                .patientWeight(p != null ? p.getPatientWeight() : null)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
