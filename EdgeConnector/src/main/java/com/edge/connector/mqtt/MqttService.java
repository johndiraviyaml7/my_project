package com.edge.connector.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.edge.connector.controller.EdgeConnectorController;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.net.ssl.*;
import java.io.InputStream;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService implements MqttCallbackExtended {

    private final SimpMessagingTemplate messagingTemplate;
    private static final Logger log = LoggerFactory.getLogger(EdgeConnectorController.class);

    private MqttClient mqttClient;

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.topic.connectivity}")
    private String connectivityTopic;

    @Value("${mqtt.topic.lwt}")
    private String lwtTopic;

    @Value("${mqtt.lwt.message}")
    private String lwtMessage;

    @Value("${mqtt.qos}")
    private int qos;

    @Value("${mqtt.keep-alive-interval:60}")
    private int keepAliveInterval;

    @Value("${mqtt.connection-timeout:30}")
    private int connectionTimeout;

    @Value("${mqtt.ssl.key-store}")
    private String keyStorePath;

    @Value("${mqtt.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${mqtt.ssl.trust-store}")
    private String trustStorePath;

    @Value("${mqtt.ssl.trust-store-password}")
    private String trustStorePassword;

    @Value("${mqtt.heartbeat.interval-seconds:60}")
    private int heartbeatIntervalSeconds;

    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean intentionalDisconnect = false;
    private volatile java.util.concurrent.ScheduledFuture<?> heartbeatFuture;

    @PostConstruct
    public void init() {
        reconnectScheduler.schedule(this::connect, 3, TimeUnit.SECONDS);
    }

    public void connect() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setServerURIs(new String[]{brokerUrl});
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setKeepAliveInterval(keepAliveInterval);
            options.setConnectionTimeout(connectionTimeout);
            options.setSocketFactory(createSslSocketFactory());

            // Set Last Will and Testament
            options.setWill(lwtTopic, lwtMessage.getBytes(), qos, true);
            log.info("LWT configured on topic '{}' with keep-alive {} seconds", lwtTopic, keepAliveInterval);

            log.info("Connecting to MQTT broker: {}", brokerUrl);
            mqttClient.connect(options);
            log.info("Connected to MQTT broker successfully");

        } catch (Exception e) {
            log.error("Failed to connect to MQTT broker: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    public void publishConnectivityStatus(String status) {
        String payload = String.format(
                "{\"status\":\"%s\",\"clientId\":\"%s\",\"timestamp\":\"%s\"}",
                status, mqttClient != null ? mqttClient.getClientId() : clientId, Instant.now().toString()
        );
        publish(connectivityTopic, payload, true);
    }

    public void publish(String topic, String payload, boolean retained) {
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                log.warn("MQTT not connected, cannot publish to topic: {}", topic);
                return;
            }
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(qos);
            message.setRetained(retained);
            mqttClient.publish(topic, message);
            log.debug("Published to [{}]: {}", topic, payload);
        } catch (MqttException e) {
            log.error("Failed to publish to topic {}: {}", topic, e.getMessage(), e);
        }
    }

    public void subscribe(String topic) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.subscribe(topic, qos);
                log.info("Subscribed to topic: {}", topic);
            }
        } catch (MqttException e) {
            log.error("Failed to subscribe to topic {}: {}", topic, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void gracefulDisconnect() {
        intentionalDisconnect = true;
        reconnectScheduler.shutdownNow();
        heartbeatScheduler.shutdownNow();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                // Force close without clean disconnect so LWT is triggered
                mqttClient.disconnectForcibly(0, 0, false);
                log.info("Forcibly disconnected from MQTT broker - LWT will be triggered");
            }
        } catch (MqttException e) {
            log.error("Error during forceful disconnect: {}", e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Disconnected from MQTT broker: {}", cause.getMessage());
        if (!intentionalDisconnect) {
            notifyWebSocketClients("disconnected", "Unexpected disconnection from MQTT broker");
            scheduleReconnect();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.debug("Message arrived on topic [{}]: {}", topic, payload);
        // Forward to WebSocket clients
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/mqtt", payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("Delivery complete for message id: {}", token.getMessageId());
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("{} to MQTT broker: {}", reconnect ? "Reconnected" : "Connected", serverURI);
        publishConnectivityStatus("connected");
        notifyWebSocketClients("connected", "Connected to MQTT broker: " + serverURI);
        startHeartbeat();
    }

    private void startHeartbeat() {
        // Cancel any existing heartbeat to prevent duplicates
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(false);
        }
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                publishConnectivityStatus("connected");
                log.debug("Heartbeat sent to {}", connectivityTopic);
            }
        }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
        log.info("Heartbeat scheduler started with interval of {} seconds", heartbeatIntervalSeconds);
    }

    private void scheduleReconnect() {
        reconnectScheduler.schedule(() -> {
            log.info("Attempting to reconnect to MQTT broker...");
            connect();
        }, 5, TimeUnit.SECONDS);
    }

    private void notifyWebSocketClients(String status, String message) {
        String notification = String.format(
                "{\"status\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                status, message, Instant.now().toString()
        );
        if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/connectivity", notification);
        }
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

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

        // Create a TrustManager that wraps the default but disables hostname verification
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        TrustManager[] trustManagers = tmf.getTrustManagers();

        // Wrap with hostname-verification-disabled trust manager for development
        TrustManager[] wrappedTrustManagers = new TrustManager[]{
            new X509ExtendedTrustManager() {
                private final X509ExtendedTrustManager delegate = (X509ExtendedTrustManager) trustManagers[0];

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                    delegate.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                    delegate.checkServerTrusted(chain, authType);
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) throws java.security.cert.CertificateException {
                    delegate.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) throws java.security.cert.CertificateException {
                    // Skip hostname verification - just verify the certificate chain
                    delegate.checkServerTrusted(chain, authType);
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
                    delegate.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, javax.net.ssl.SSLEngine engine) throws java.security.cert.CertificateException {
                    // Skip hostname verification - just verify the certificate chain
                    delegate.checkServerTrusted(chain, authType);
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return delegate.getAcceptedIssuers();
                }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), wrappedTrustManagers, new SecureRandom());

        // Enforce TLS 1.3 as minimum version
        SSLSocketFactory factory = sslContext.getSocketFactory();
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        sslParams.setProtocols(new String[]{"TLSv1.3"});

        log.warn("SSL hostname verification is DISABLED - for development use only!");
        log.info("TLS 1.3 enforced as minimum protocol version");
        return new SSLSocketFactoryWrapper(factory, sslParams);
    }

    private InputStream resolveResource(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            return getClass().getClassLoader().getResourceAsStream(path.substring("classpath:".length()));
        }
        return new java.io.FileInputStream(path);
    }

    private static class SSLSocketFactoryWrapper extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String[] enabledProtocols;

        SSLSocketFactoryWrapper(SSLSocketFactory delegate, SSLParameters sslParams) {
            this.delegate = delegate;
            this.enabledProtocols = sslParams.getProtocols();
        }

        private java.net.Socket applyParams(java.net.Socket socket) {
            if (socket instanceof SSLSocket sslSocket) {
                sslSocket.setEnabledProtocols(enabledProtocols);
            }
            return socket;
        }

        @Override
        public java.net.Socket createSocket() throws java.io.IOException {
            SSLSocket socket = (SSLSocket) delegate.createSocket();
            socket.setEnabledProtocols(enabledProtocols);
            return socket;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            return applyParams(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            return applyParams(delegate.createSocket(host, port));
        }

        @Override
        public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException {
            return applyParams(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
            return applyParams(delegate.createSocket(host, port));
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException {
            return applyParams(delegate.createSocket(address, port, localAddress, localPort));
        }
    }
}
