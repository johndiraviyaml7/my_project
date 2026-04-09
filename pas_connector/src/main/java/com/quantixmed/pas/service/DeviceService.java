package com.quantixmed.pas.service;

import com.quantixmed.pas.dto.PasDtos.DeviceStatusDto;
import com.quantixmed.pas.dto.PasDtos.RegisterRequest;
import com.quantixmed.pas.dto.PasDtos.RegisterResponse;
import com.quantixmed.pas.model.PacsDevice;
import com.quantixmed.pas.repository.PacsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final PacsDeviceRepository deviceRepo;

    @Value("${pas.device.heartbeat-timeout-seconds:30}")
    private long heartbeatTimeoutSeconds;

    /** Upsert by serial number. Called from /register after mTLS verified the caller. */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        PacsDevice device = deviceRepo.findBySerialNumber(req.getSerialNumber())
                .orElseGet(() -> PacsDevice.builder().serialNumber(req.getSerialNumber()).build());

        device.setName(req.getDeviceName());
        device.setModality(req.getModality());
        device.setLocation(req.getInstituteName());  // institute -> location
        if (req.getManufacturer() != null) device.setManufacturer(req.getManufacturer());
        if (req.getModel() != null)        device.setModel(req.getModel());
        if (req.getAeTitle() != null)      device.setAeTitle(req.getAeTitle());
        if (device.getStatus() == null || "Disconnected".equals(device.getStatus())) {
            device.setStatus("Registered");
        }
        device.setIsActive(true);

        device = deviceRepo.save(device);
        log.info("Device registered: serial={} id={} name={}",
                device.getSerialNumber(), device.getId(), device.getName());

        return RegisterResponse.builder()
                .id(device.getId())
                .serialNumber(device.getSerialNumber())
                .deviceName(device.getName())
                .status(device.getStatus())
                .message("Device registered successfully")
                .registeredAt(device.getUpdatedAt() != null ? device.getUpdatedAt() : Instant.now())
                .build();
    }

    @Transactional
    public void markHeartbeat(String serialNumber) {
        deviceRepo.findBySerialNumber(serialNumber).ifPresent(d -> {
            d.setLastSeenAt(Instant.now());
            if (!"Connected".equals(d.getStatus())) {
                d.setStatus("Connected");
                log.info("Device {} -> Connected", serialNumber);
            }
            deviceRepo.save(d);
        });
    }

    @Transactional
    public void markDisconnected(String serialNumber) {
        deviceRepo.findBySerialNumber(serialNumber).ifPresent(d -> {
            d.setStatus("Disconnected");
            deviceRepo.save(d);
            log.info("Device {} -> Disconnected (LWT or timeout)", serialNumber);
        });
    }

    /** Sweep devices that haven't sent a heartbeat within the timeout and
     *  flip them to Disconnected.  Called by the scheduled job. */
    @Transactional
    public int sweepStale() {
        Instant cutoff = Instant.now().minusSeconds(heartbeatTimeoutSeconds);
        int flipped = 0;
        for (PacsDevice d : deviceRepo.findAll()) {
            if ("Connected".equals(d.getStatus())
                && d.getLastSeenAt() != null
                && d.getLastSeenAt().isBefore(cutoff)) {
                d.setStatus("Disconnected");
                deviceRepo.save(d);
                flipped++;
            }
        }
        return flipped;
    }

    @Transactional(readOnly = true)
    public List<DeviceStatusDto> listAll() {
        Instant now = Instant.now();
        return deviceRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(d -> {
                    Long secs = d.getLastSeenAt() == null ? null
                            : Duration.between(d.getLastSeenAt(), now).getSeconds();
                    boolean live = secs != null && secs <= heartbeatTimeoutSeconds;
                    return DeviceStatusDto.builder()
                            .id(d.getId())
                            .serialNumber(d.getSerialNumber())
                            .name(d.getName())
                            .modality(d.getModality())
                            .instituteName(d.getLocation())
                            .status(d.getStatus())
                            .lastSeenAt(d.getLastSeenAt())
                            .secondsSinceLastSeen(secs)
                            .isLive(live)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public PacsDevice findBySerial(String serial) {
        return deviceRepo.findBySerialNumber(serial).orElse(null);
    }
}
