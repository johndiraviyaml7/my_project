package com.quantixmed.dicom.service;

import com.quantixmed.dicom.dto.DicomDtos.SeriesDto;
import com.quantixmed.dicom.model.Series;
import com.quantixmed.dicom.repository.SeriesRepository;
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
public class SeriesService {

    private final SeriesRepository seriesRepo;

    public List<SeriesDto> listByStudy(UUID studyId) {
        return seriesRepo.findByStudyIdOrdered(studyId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public SeriesDto getSeries(UUID id) {
        Series s = seriesRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Series not found: " + id));
        return toDto(s);
    }

    private SeriesDto toDto(Series s) {
        return SeriesDto.builder()
                .id(s.getId())
                .studyId(s.getStudy() != null ? s.getStudy().getId() : null)
                .seriesInstanceUid(s.getSeriesInstanceUid())
                .seriesNumber(s.getSeriesNumber())
                .seriesDate(s.getSeriesDate())
                .seriesDescription(s.getSeriesDescription())
                .modality(s.getModality())
                .bodyPartExamined(s.getBodyPartExamined())
                .protocolName(s.getProtocolName())
                .manufacturer(s.getManufacturer())
                .manufacturerModel(s.getManufacturerModel())
                .sliceThickness(s.getSliceThickness())
                .pixelSpacingRow(s.getPixelSpacingRow())
                .pixelSpacingCol(s.getPixelSpacingCol())
                .patientPosition(s.getPatientPosition())
                .imageCount(s.getImageCount())
                .orthancSeriesId(s.getOrthancSeriesId())
                .deidSeriesInstanceUid(s.getDeidSeriesInstanceUid())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
