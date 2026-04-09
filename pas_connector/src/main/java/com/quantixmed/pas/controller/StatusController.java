package com.quantixmed.pas.controller;

import com.quantixmed.pas.dto.PasDtos.DeviceStatusDto;
import com.quantixmed.pas.dto.PasDtos.StatusEventDto;
import com.quantixmed.pas.service.DeviceService;
import com.quantixmed.pas.service.StatusEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Plain-HTTP endpoints used by the React dashboard (no client cert).
 * These are exposed on management port 8444, not the mTLS port 8443.
 */
@RestController
@RequestMapping("/api/pas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StatusController {

    private final DeviceService deviceService;
    private final StatusEventService eventService;

    @GetMapping("/devices/status")
    public ResponseEntity<List<DeviceStatusDto>> allDevices() {
        return ResponseEntity.ok(deviceService.listAll());
    }

    @GetMapping("/events/recent")
    public ResponseEntity<List<StatusEventDto>> recentEvents() {
        return ResponseEntity.ok(eventService.listRecent());
    }

    @GetMapping("/devices/{serial}/events")
    public ResponseEntity<List<StatusEventDto>> deviceEvents(@PathVariable String serial) {
        return ResponseEntity.ok(eventService.listForDevice(serial));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "ok",
                "service", "pas-connector",
                "version", "1.0.0"
        ));
    }
}
