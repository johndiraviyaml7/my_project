package com.edge.viewer.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.InputStream;
import java.security.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

@Slf4j
@Service
public class ViewerMqttService implements MqttCallback {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.qos}")
    private int qos;

    @Value("${mqtt.topic.connectivity}")
    private String connectivityTopic;

    @Value("${mqtt.topic.lwt}")
    private String lwtTopic;

    @Value("${mqtt.lwt.message}")
    private String lwtMessage;

    @Value("${mqtt.ssl.key-store}")
    private String keyStorePath;

    @Value("${mqtt.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${mqtt.ssl.trust-store}")
    private String trustStorePath;

    @Value("${mqtt.ssl.trust-store-password}")
    private String trustStorePassword;

    private IMqttClient mqttClient;
    private final List<BiConsumer<String, String>> messageListeners = new ArrayList<>();
    private final List<BiConsumer<Boolean, String>> connectionListeners = new ArrayList<>();

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(this);
            connect();
        } catch (MqttException e) {
            log.error("Failed to initialize MQTT client: {}", e.getMessage(), e);
        }
    }

    public void connect() {
        try {
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setServerURIs(new String[]{brokerUrl});
            options.setUserName(username);
            options.setPassword(password.getBytes());
            options.setAutomaticReconnect(true);
            options.setCleanStart(false);

            // LWT for ungraceful disconnect
            String lwtPayload = lwtMessage.replace("\"timestamp\":\"\"",
                    "\"timestamp\":\"" + Instant.now().toString() + "\"");
            MqttMessage lwtMsg = new MqttMessage(lwtPayload.getBytes());
            lwtMsg.setQos(qos);
            lwtMsg.setRetained(true);
            options.setWill(lwtTopic, lwtMsg);

            options.setSocketFactory(createSslSocketFactory());
            mqttClient.connect(options);
            log.info("Viewer MQTT connected to {}", brokerUrl);

            // Subscribe to connectivity topic to monitor EdgeConnector
            mqttClient.subscribe(connectivityTopic, qos);

        } catch (Exception e) {
            log.error("MQTT connection failed: {}", e.getMessage(), e);
        }
    }

    public void publishConnectivityOn() {
        String payload = String.format(
                "{\"status\":\"connected\",\"source\":\"viewer\",\"timestamp\":\"%s\"}",
                Instant.now().toString()
        );
        publish(connectivityTopic, payload, true);
        log.info("Published MQTT connectivity ON");
    }

    public void publishLwtOff() {
        String payload = String.format(
                "{\"status\":\"disconnected\",\"source\":\"viewer\",\"reason\":\"manual_shutdown\",\"timestamp\":\"%s\"}",
                Instant.now().toString()
        );
        publish(lwtTopic, payload, true);
        log.info("Published LWT OFF (manual shutdown)");
    }

    public void publish(String topic, String payload, boolean retained) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(qos);
                message.setRetained(retained);
                mqttClient.publish(topic, message);
            }
        } catch (MqttException e) {
            log.error("Publish failed: {}", e.getMessage(), e);
        }
    }

    public void addMessageListener(BiConsumer<String, String> listener) {
        messageListeners.add(listener);
    }

    public void addConnectionListener(BiConsumer<Boolean, String> listener) {
        connectionListeners.add(listener);
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                publishLwtOff();
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            log.error("Disconnect error: {}", e.getMessage(), e);
        }
    }

    @Override
    public void disconnected(MqttDisconnectResponse response) {
        log.warn("MQTT disconnected: {}", response.getReasonString());
        connectionListeners.forEach(l -> l.accept(false, response.getReasonString()));
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        log.error("MQTT error: {}", exception.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.debug("Message on [{}]: {}", topic, payload);
        messageListeners.forEach(l -> l.accept(topic, payload));
    }

    @Override
    public void deliveryComplete(IMqttToken token) {}

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("{} MQTT: {}", reconnect ? "Reconnected" : "Connected", serverURI);
        connectionListeners.forEach(l -> l.accept(true, serverURI));
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {}

    private SSLSocketFactory createSslSocketFactory() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream ks = resolveResource(keyStorePath)) {
            keyStore.load(ks, keyStorePassword.toCharArray());
        }
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream ts = resolveResource(trustStorePath)) {
            trustStore.load(ts, trustStorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return sslContext.getSocketFactory();
    }

    private InputStream resolveResource(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            return getClass().getClassLoader().getResourceAsStream(path.substring("classpath:".length()));
        }
        return new java.io.FileInputStream(path);
    }
}
