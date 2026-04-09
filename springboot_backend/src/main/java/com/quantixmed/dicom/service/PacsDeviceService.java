package com.quantixmed.dicom.service;

import com.quantixmed.dicom.dto.DicomDtos.DeviceCreateDto;
import com.quantixmed.dicom.dto.DicomDtos.DeviceDto;
import com.quantixmed.dicom.model.PacsDevice;
import com.quantixmed.dicom.repository.PacsDeviceRepository;
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
public class PacsDeviceService {

    private final PacsDeviceRepository deviceRepo;
    private final SubjectRepository subjectRepo;

    public List<DeviceDto> listDevices() {
        return deviceRepo.findAllByIsActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public DeviceDto getDevice(UUID id) {
        PacsDevice d = deviceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found: " + id));
        return toDto(d);
    }

    @Transactional
    public DeviceDto createDevice(DeviceCreateDto req) {
        PacsDevice d = PacsDevice.builder()
                .name(req.getName())
                .serialNumber(req.getSerialNumber())
                .modality(req.getModality())
                .status(req.getStatus() != null ? req.getStatus() : "Active")
                .location(req.getLocation())
                .manufacturer(req.getManufacturer())
                .model(req.getModel())
                .ipAddress(req.getIpAddress())
                .port(req.getPort() != null ? req.getPort() : 11112)
                .aeTitle(req.getAeTitle())
                .description(req.getDescription())
                .isActive(true)
                .build();
        d = deviceRepo.save(d);
        log.info("PACS device created: {} ({})", d.getName(), d.getId());
        return toDto(d);
    }

    @Transactional
    public DeviceDto updateDevice(UUID id, DeviceCreateDto req) {
        PacsDevice d = deviceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found: " + id));
        d.setName(req.getName());
        d.setModality(req.getModality());
        d.setStatus(req.getStatus());
        d.setLocation(req.getLocation());
        d.setManufacturer(req.getManufacturer());
        d.setModel(req.getModel());
        d.setIpAddress(req.getIpAddress());
        d.setPort(req.getPort());
        d.setAeTitle(req.getAeTitle());
        d.setDescription(req.getDescription());
        return toDto(deviceRepo.save(d));
    }

    @Transactional
    public void deleteDevice(UUID id) {
        deviceRepo.deleteById(id);
    }

    private DeviceDto toDto(PacsDevice d) {
        var subjects = subjectRepo.findByDeviceIdOrderByCreatedAtDesc(d.getId());
        long subjectCount = subjects.size();
        long instanceCount = subjects.stream()
                .mapToLong(s -> s.getTotalInstances() == null ? 0L : s.getTotalInstances())
                .sum();
        return DeviceDto.builder()
                .id(d.getId())
                .name(d.getName())
                .serialNumber(d.getSerialNumber())
                .modality(d.getModality())
                .status(d.getStatus())
                .location(d.getLocation())
                .manufacturer(d.getManufacturer())
                .model(d.getModel())
                .ipAddress(d.getIpAddress())
                .port(d.getPort())
                .aeTitle(d.getAeTitle())
                .description(d.getDescription())
                .isActive(d.getIsActive())
                .subjectCount(subjectCount)
                .instanceCount(instanceCount)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
