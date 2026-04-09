package com.quantixmed.dicom.service;

import com.quantixmed.dicom.dto.DicomDtos.InstanceDto;
import com.quantixmed.dicom.model.Instance;
import com.quantixmed.dicom.repository.InstanceRepository;
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
public class InstanceService {

    private final InstanceRepository instanceRepo;

    public List<InstanceDto> listBySeries(UUID seriesId) {
        return instanceRepo.findBySeriesIdOrderByInstanceNumber(seriesId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public InstanceDto getInstance(UUID id) {
        Instance i = instanceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + id));
        return toDto(i);
    }

    private InstanceDto toDto(Instance i) {
        return InstanceDto.builder()
                .id(i.getId())
                .seriesId(i.getSeries() != null ? i.getSeries().getId() : null)
                .sopInstanceUid(i.getSopInstanceUid())
                .sopClassUid(i.getSopClassUid())
                .instanceNumber(i.getInstanceNumber())
                .acquisitionDate(i.getAcquisitionDate())
                .rows(i.getRows())
                .cols(i.getCols())
                .bitsAllocated(i.getBitsAllocated())
                .photometricInterp(i.getPhotometricInterp())
                .sliceLocation(i.getSliceLocation())
                .fileSizeBytes(i.getFileSizeBytes())
                .filePath(i.getFilePath())
                .deidFilePath(i.getDeidFilePath())
                .orthancInstanceId(i.getOrthancInstanceId())
                .deidSopInstanceUid(i.getDeidSopInstanceUid())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
