package com.quantixmed.dicom.controller;

import com.quantixmed.dicom.dto.DicomDtos.DeviceCreateDto;
import com.quantixmed.dicom.dto.DicomDtos.DeviceDto;
import com.quantixmed.dicom.service.PacsDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "PACS Devices")
@CrossOrigin(origins = "*")
public class PacsDeviceController {

    private final PacsDeviceService deviceService;

    @GetMapping
    @Operation(summary = "List all PACS devices")
    public ResponseEntity<List<DeviceDto>> list() {
        return ResponseEntity.ok(deviceService.listDevices());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a PACS device by ID")
    public ResponseEntity<DeviceDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(deviceService.getDevice(id));
    }

    @PostMapping
    @Operation(summary = "Register a new PACS device")
    public ResponseEntity<DeviceDto> create(@RequestBody DeviceCreateDto req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceService.createDevice(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a PACS device")
    public ResponseEntity<DeviceDto> update(@PathVariable UUID id,
                                             @RequestBody DeviceCreateDto req) {
        return ResponseEntity.ok(deviceService.updateDevice(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a PACS device")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }
}
