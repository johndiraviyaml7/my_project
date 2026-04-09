package com.quantixmed.pas.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantixmed.pas.service.DeviceService;
import com.quantixmed.pas.service.StatusEventService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs inside the same JVM as the Moquette broker, connects back to the
 * plain localhost:1883 listener, and subscribes to the three PACS topics.
 *
 * Topic structure:
 *   pacs/{serial}/status          - JSON heartbeat from Edge Connector
 *   pacs/{serial}/lwt             - Last Will & Testament on unclean disconnect
 *   pacs/{serial}/upload-status   - upload start/progress/complete events
 *
 * On every message we:
 *   1) parse the serial from the topic
 *   2) update pacs_devices.status + last_seen_at
 *   3) append a row to pacs_status_events for the live dashboard log
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MqttSelfSubscriber implements MqttCallback {

    private final DeviceService deviceService;
    private final StatusEventService eventService;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${pas.mqtt.plain-port}")    private int plainPort;
    @Value("${pas.mqtt.self-client-id}") private String clientId;

    private MqttClient client;

    @PostConstruct
    public void start() {
        // Delay connection slightly to let the broker finish starting.
        // Moquette's startServer() is blocking so by the time @PostConstruct
        // runs on us, the broker bean has finished — but being explicit doesn't hurt.
        new Thread(this::connectLoop, "mqtt-self-subscriber").start();
    }

    private void connectLoop() {
        int attempt = 0;
        while (attempt < 20 && (client == null || !client.isConnected())) {
            attempt++;
            try {
                Thread.sleep(500);
                String broker = "tcp://localhost:" + plainPort;
                client = new MqttClient(broker, clientId, null);
                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setAutomaticReconnect(true);
                opts.setCleanSession(true);
                opts.setConnectionTimeout(10);
                opts.setKeepAliveInterval(20);
                opts.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
                client.setCallback(this);
                client.connect(opts);
                client.subscribe("pacs/+/status",        1);
                client.subscribe("pacs/+/lwt",           1);
                client.subscribe("pacs/+/upload-status", 1);
                log.info("Self-subscriber connected to {} and subscribed to pacs/+/{{status,lwt,upload-status}}", broker);
                return;
            } catch (Exception e) {
                log.warn("Self-subscriber connect attempt {} failed: {}", attempt, e.getMessage());
            }
        }
        log.error("Self-subscriber gave up after {} attempts", attempt);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT self-subscriber lost connection: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.debug("MQTT recv topic={} payload={}", topic, payload);

        String serial = extractSerial(topic);
        if (serial == null) {
            log.warn("Unparseable topic: {}", topic);
            return;
        }

        try {
            if (topic.endsWith("/status")) {
                deviceService.markHeartbeat(serial);
                eventService.record(serial, "HEARTBEAT", topic, payload);
            } else if (topic.endsWith("/lwt")) {
                deviceService.markDisconnected(serial);
                eventService.record(serial, "LWT", topic, payload);
            } else if (topic.endsWith("/upload-status")) {
                String phase = extractPhase(payload);
                eventService.record(serial, "UPLOAD_" + phase, topic, payload);
            }
        } catch (Exception e) {
            log.error("Failed handling {}: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { /* publisher side, unused */ }

    private String extractSerial(String topic) {
        // pacs/{serial}/...
        String[] parts = topic.split("/");
        return parts.length >= 3 ? parts[1] : null;
    }

    private String extractPhase(String payload) {
        try {
            var node = json.readTree(payload);
            String phase = node.path("phase").asText("");
            return phase.isBlank() ? "EVENT" : phase.toUpperCase();
        } catch (Exception e) {
            return "EVENT";
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
            }
        } catch (MqttException e) {
            log.warn("Error stopping self-subscriber: {}", e.getMessage());
        }
    }
}
