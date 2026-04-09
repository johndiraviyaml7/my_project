package com.quantixmed.pas.service;

import com.quantixmed.pas.dto.PasDtos.StatusEventDto;
import com.quantixmed.pas.model.PacsDevice;
import com.quantixmed.pas.model.StatusEvent;
import com.quantixmed.pas.repository.StatusEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatusEventService {

    private final StatusEventRepository eventRepo;
    private final DeviceService deviceService;

    @Transactional
    public void record(String serialNumber, String eventType, String topic, String payload) {
        PacsDevice d = deviceService.findBySerial(serialNumber);
        StatusEvent e = StatusEvent.builder()
                .deviceId(d != null ? d.getId() : null)
                .serialNumber(serialNumber)
                .eventType(eventType)
                .topic(topic)
                .payload(payload)
                .build();
        eventRepo.save(e);
    }

    @Transactional(readOnly = true)
    public List<StatusEventDto> listRecent() {
        return eventRepo.findTop50ByOrderByOccurredAtDesc().stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StatusEventDto> listForDevice(String serialNumber) {
        return eventRepo.findTop50BySerialNumberOrderByOccurredAtDesc(serialNumber).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    private StatusEventDto toDto(StatusEvent e) {
        return StatusEventDto.builder()
                .id(e.getId())
                .deviceId(e.getDeviceId())
                .serialNumber(e.getSerialNumber())
                .eventType(e.getEventType())
                .topic(e.getTopic())
                .payload(e.getPayload())
                .occurredAt(e.getOccurredAt())
                .build();
    }
}
