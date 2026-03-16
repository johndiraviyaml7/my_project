package com.pacs.connector.service;

import com.pacs.connector.model.PacsConnectivityStatus;
import com.pacs.connector.model.PacsDevice;
import com.pacs.connector.repository.PacsConnectivityStatusRepository;
import com.pacs.connector.repository.PacsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectivityStatusService {

    private final PacsDeviceRepository deviceRepository;
    private final PacsConnectivityStatusRepository statusRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void recordConnectivityEvent(String mqttClientId, String status,
                                        String messageSource, String reason,
                                        String topic, String rawPayload) {
        // Find or auto-register device
        PacsDevice device = deviceRepository.findByMqttClientId(mqttClientId)
                .orElseGet(() -> registerNewDevice(mqttClientId));

        PacsConnectivityStatus record = PacsConnectivityStatus.builder()
                .pacsDevice(device)
                .status(status)
                .messageSource(messageSource)
                .disconnectReason(reason)
                .mqttTopic(topic)
                .rawPayload(rawPayload)
                .build();

        PacsConnectivityStatus saved = statusRepository.save(record);
        log.info("Recorded connectivity event: device={}, status={}, source={}",
                device.getDeviceName(), status, messageSource);

        // Notify WebSocket clients
        String notification = String.format(
                "{\"deviceId\":\"%s\",\"deviceName\":\"%s\",\"status\":\"%s\",\"source\":\"%s\",\"timestamp\":\"%s\"}",
                device.getId().toString(), device.getDeviceName(), status, messageSource,
                LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/pacs-connectivity", notification);
    }

    @Transactional(readOnly = true)
    public List<PacsConnectivityStatus> getStatusHistory(UUID deviceId) {
        return statusRepository.findByPacsDeviceIdOrderByReceivedAtDesc(deviceId);
    }

    @Transactional(readOnly = true)
    public List<PacsConnectivityStatus> getLwtEvents() {
        return statusRepository.findByMessageSourceOrderByReceivedAtDesc("lwt");
    }

    private PacsDevice registerNewDevice(String mqttClientId) {
        PacsDevice device = PacsDevice.builder()
                .deviceName(mqttClientId)
                .mqttClientId(mqttClientId)
                .deviceType("EdgeConnector")
                .description("Auto-registered from MQTT connection")
                .active(true)
                .build();
        PacsDevice saved = deviceRepository.save(device);
        log.info("Auto-registered new PACS device: {}", mqttClientId);
        return saved;
    }
}
