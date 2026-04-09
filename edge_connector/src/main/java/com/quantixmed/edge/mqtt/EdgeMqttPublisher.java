package com.quantixmed.edge.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantixmed.edge.service.TlsContextFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes:
 *   pacs/{serial}/status         every {@code edge.heartbeat.interval-seconds}
 *   pacs/{serial}/lwt            as Last Will on unclean disconnect
 *   pacs/{serial}/upload-status  when caller invokes {@link #publishUploadStatus}
 *
 * Connects using a TLSv1.3 SSLContext with client certificate auth.
 * Uses MQTT v3.1.1 (MqttConnectOptions.MQTT_VERSION_3_1_1).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EdgeMqttPublisher {

    private final TlsContextFactory tlsFactory;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${edge.pas.mqtt-url}") private String brokerUrl;
    @Value("${edge.heartbeat.interval-seconds:10}") private int heartbeatSeconds;

    private volatile MqttClient client;
    private volatile String serialNumber;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicLong heartbeatCount = new AtomicLong(0);
    private volatile Instant lastHeartbeatAt;
    private volatile String lastError;

    /** Called by RegistrationService after /register succeeds. */
    public synchronized void connectAs(String serial) {
        this.serialNumber = serial;
        disconnectQuietly();
        try {
            SSLContext ctx = tlsFactory.buildContext();

            String clientId = "edge-" + serial + "-" + System.currentTimeMillis() % 100000;
            client = new MqttClient(brokerUrl, clientId, null);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setSocketFactory(ctx.getSocketFactory());
            opts.setHttpsHostnameVerificationEnabled(false); // self-signed dev certs
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            opts.setConnectionTimeout(10);
            opts.setKeepAliveInterval(15);
            opts.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

            // Last Will & Testament: "I went away uncleanly"
            String lwtTopic = "pacs/" + serial + "/lwt";
            String lwtPayload = "{\"serial\":\"" + serial
                    + "\",\"reason\":\"unclean_disconnect\",\"ts\":\"" + Instant.now() + "\"}";
            opts.setWill(lwtTopic, lwtPayload.getBytes(), 1, false);

            client.setCallback(new MqttCallback() {
                public void connectionLost(Throwable cause) {
                    connected.set(false);
                    lastError = cause != null ? cause.getMessage() : "lost";
                    log.warn("MQTT connection lost: {}", lastError);
                }
                public void messageArrived(String topic, MqttMessage message) { /* publisher */ }
                public void deliveryComplete(IMqttDeliveryToken token) { /* noop */ }
            });

            client.connect(opts);
            connected.set(true);
            lastError = null;
            log.info("Edge MQTT connected: {} as {}", brokerUrl, clientId);

            // Publish an immediate "hello" heartbeat so the dashboard flips green fast
            publishHeartbeat();
        } catch (Exception e) {
            connected.set(false);
            lastError = e.getMessage();
            log.error("MQTT connect failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${edge.heartbeat.interval-seconds:10}000")
    public void heartbeatTick() {
        if (serialNumber != null && client != null && client.isConnected()) {
            publishHeartbeat();
        }
    }

    private void publishHeartbeat() {
        try {
            Map<String,Object> payload = new HashMap<>();
            payload.put("serial", serialNumber);
            payload.put("seq",    heartbeatCount.incrementAndGet());
            payload.put("ts",     Instant.now().toString());
            client.publish("pacs/" + serialNumber + "/status",
                    json.writeValueAsBytes(payload), 1, false);
            lastHeartbeatAt = Instant.now();
        } catch (Exception e) {
            log.warn("Heartbeat publish failed: {}", e.getMessage());
            lastError = e.getMessage();
        }
    }

    public void publishUploadStatus(String phase, String detail) {
        if (serialNumber == null || client == null || !client.isConnected()) return;
        try {
            Map<String,Object> payload = new HashMap<>();
            payload.put("serial", serialNumber);
            payload.put("phase",  phase);                // STARTED | COMPLETED | FAILED
            payload.put("detail", detail != null ? detail : "");
            payload.put("ts",     Instant.now().toString());
            client.publish("pacs/" + serialNumber + "/upload-status",
                    json.writeValueAsBytes(payload), 1, false);
        } catch (Exception e) {
            log.warn("Upload-status publish failed: {}", e.getMessage());
        }
    }

    public boolean isConnected() { return connected.get(); }
    public String getSerialNumber() { return serialNumber; }
    public long getHeartbeatCount() { return heartbeatCount.get(); }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public String getLastError() { return lastError; }

    @PreDestroy
    public void shutdown() { disconnectQuietly(); }

    private void disconnectQuietly() {
        if (client != null) {
            try {
                if (client.isConnected()) client.disconnect();
                client.close();
            } catch (MqttException ignored) { }
            client = null;
        }
        connected.set(false);
    }
}
