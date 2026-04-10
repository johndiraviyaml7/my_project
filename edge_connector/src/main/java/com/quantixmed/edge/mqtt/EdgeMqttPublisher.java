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
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
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
 * Uses MQTT v3.1.1.
 *
 * Hostname verification is disabled at the socket level via a custom
 * SSLSocketFactory wrapper — Paho's setHttpsHostnameVerificationEnabled
 * flag only affects WebSocket connections, not raw ssl:// URIs.
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

    /**
     * Wraps an SSLSocketFactory and disables endpoint-identification
     * (i.e. JDK hostname verification) on every socket it produces.
     * Required because the dev server cert has CN=MedServer and no SAN,
     * but Edge connects to ssl://localhost:8883 — the JDK would otherwise
     * reject the handshake with "No name matching localhost found".
     */
    private static final class NoVerifySocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        NoVerifySocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }

        private Socket disableVerify(Socket s) {
            if (s instanceof SSLSocket ssl) {
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm(null);
                ssl.setSSLParameters(params);
            }
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }

        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return disableVerify(delegate.createSocket(s, host, port, autoClose));
        }
        @Override public Socket createSocket(String host, int port) throws IOException {
            return disableVerify(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return disableVerify(delegate.createSocket(host, port, localHost, localPort));
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return disableVerify(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return disableVerify(delegate.createSocket(address, port, localAddress, localPort));
        }
        @Override public Socket createSocket() throws IOException {
            return disableVerify(delegate.createSocket());
        }
    }

    /** Called by EdgeController.register after /api/pas/register succeeds. */
    public synchronized void connectAs(String serial) {
        log.info("connectAs({}) called — broker={}", serial, brokerUrl);
        this.serialNumber = serial;
        disconnectQuietly();
        try {
            SSLContext ctx = tlsFactory.buildMqttContext();
            SSLSocketFactory wrapped = new NoVerifySocketFactory(ctx.getSocketFactory());

            String clientId = "edge-" + serial + "-" + (System.currentTimeMillis() % 100000);
            log.info("Creating MqttClient: brokerUrl={} clientId={}", brokerUrl, clientId);
            client = new MqttClient(brokerUrl, clientId, null);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setSocketFactory(wrapped);
            opts.setHttpsHostnameVerificationEnabled(false); // belt-and-braces
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            opts.setConnectionTimeout(10);
            opts.setKeepAliveInterval(15);
            opts.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

            String lwtTopic = "pacs/" + serial + "/lwt";
            String lwtPayload = "{\"serial\":\"" + serial
                    + "\",\"reason\":\"unclean_disconnect\",\"ts\":\"" + Instant.now() + "\"}";
            opts.setWill(lwtTopic, lwtPayload.getBytes(), 1, false);
            log.info("LWT set on topic={} payload={}", lwtTopic, lwtPayload);

            client.setCallback(new MqttCallback() {
                public void connectionLost(Throwable cause) {
                    connected.set(false);
                    lastError = cause != null ? cause.getMessage() : "lost";
                    log.warn("MQTT connection lost: {}", lastError, cause);
                }
                public void messageArrived(String topic, MqttMessage message) { /* publisher */ }
                public void deliveryComplete(IMqttDeliveryToken token) { /* noop */ }
            });

            log.info("Calling client.connect() ...");
            client.connect(opts);
            connected.set(true);
            lastError = null;
            log.info("✓ Edge MQTT connected: {} as {}", brokerUrl, clientId);

            // Publish an immediate "hello" heartbeat so the dashboard flips green fast
            publishHeartbeat();
        } catch (Throwable t) {
            connected.set(false);
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            log.error("✗ MQTT connect FAILED — {}", lastError, t);
        }
    }

    /** Idempotent reconnect — used by /edge/reconnect for manual recovery. */
    public synchronized void reconnect() {
        if (serialNumber == null) {
            log.warn("reconnect() called but no serial — must register first");
            return;
        }
        connectAs(serialNumber);
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
            log.debug("Heartbeat #{} sent", heartbeatCount.get());
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
