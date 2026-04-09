package com.quantixmed.dicom.service;

import com.quantixmed.dicom.dto.DicomDtos.StudyDto;
import com.quantixmed.dicom.model.Patient;
import com.quantixmed.dicom.model.Study;
import com.quantixmed.dicom.model.Subject;
import com.quantixmed.dicom.repository.PatientRepository;
import com.quantixmed.dicom.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StudyService {

    private final StudyRepository studyRepo;
    private final PatientRepository patientRepo;

    @Value("${app.ohif.viewer-url:http://localhost:3000}")
    private String ohifViewerUrl;

    @Value("${app.ohif.dicomweb-root:http://localhost:8080/orthanc/dicom-web}")
    private String wadoRsRoot;

    public List<StudyDto> listBySubject(UUID subjectId) {
        return studyRepo.findBySubjectIdSorted(subjectId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public StudyDto getStudy(UUID id) {
        Study s = studyRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Study not found: " + id));
        return toDto(s);
    }

    private StudyDto toDto(Study s) {
        Subject subj = s.getSubject();
        UUID subjectUuid = subj != null ? subj.getId() : null;
        Patient pat = subjectUuid != null
                ? patientRepo.findBySubjectId(subjectUuid).orElse(null)
                : null;

        // OHIF deep-link — built only when we have an Orthanc UID
        String orthancUid = s.getOrthancStudyUid() != null
                ? s.getOrthancStudyUid()
                : s.getDeidStudyInstanceUid();
        String ohifUrl = null;
        boolean hasViewer = false;
        if (orthancUid != null && !orthancUid.isBlank()) {
            String enc = URLEncoder.encode(wadoRsRoot, StandardCharsets.UTF_8);
            ohifUrl = ohifViewerUrl + "/viewer?StudyInstanceUIDs=" + orthancUid
                    + "&wadoRsRoot=" + enc;
            hasViewer = s.getOrthancStudyId() != null;
        }

        return StudyDto.builder()
                .id(s.getId())
                .subjectId(subjectUuid)
                .subjectLabel(subj != null ? subj.getSubjectId() : null)
                .studyInstanceUid(s.getStudyInstanceUid())
                .studyDate(s.getStudyDate())
                .studyTime(s.getStudyTime())
                .studyDescription(s.getStudyDescription())
                .studyId(s.getStudyId())
                .accessionNumber(s.getAccessionNumber())
                .totalSeries(s.getTotalSeries())
                .totalInstances(s.getTotalInstances())
                .orthancStudyId(s.getOrthancStudyId())
                .orthancStudyUid(s.getOrthancStudyUid())
                .wadoStudyUrl(s.getWadoStudyUrl())
                .ohifViewerUrl(ohifUrl)
                .hasViewer(hasViewer)
                .deidStudyInstanceUid(s.getDeidStudyInstanceUid())
                .deidStudyDate(s.getDeidStudyDate())
                // Subject context
                .subjectCollection(subj != null ? subj.getCollection() : null)
                .subjectSite(       subj != null ? subj.getSite() : null)
                .subjectSpecies(    subj != null ? subj.getSpeciesDescription() : null)
                .subjectIsPhantom(  subj != null ? subj.getIsPhantom() : null)
                .deidSubjectId(     subj != null ? subj.getDeidSubjectId() : null)
                // Patient context
                .patientId(    pat != null ? pat.getDicomPatientId() : null)
                .patientSex(   pat != null ? pat.getPatientSex()     : null)
                .patientAge(   pat != null ? pat.getPatientAge()     : null)
                .patientWeight(pat != null ? pat.getPatientWeight()  : null)
                .patientSize(  pat != null ? pat.getPatientSize()    : null)
                .deidPatientId(pat != null ? pat.getDeidPatientId()  : null)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
