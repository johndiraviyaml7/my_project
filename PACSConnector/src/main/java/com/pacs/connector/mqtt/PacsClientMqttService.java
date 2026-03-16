package com.pacs.connector.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacs.connector.service.ConnectivityStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.net.ssl.*;
import java.io.InputStream;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service  // MQTT client disabled - enable later
@RequiredArgsConstructor
public class PacsClientMqttService implements MqttCallbackExtended {

    private final ConnectivityStatusService connectivityStatusService;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.client.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.client.username}")
    private String username;

    @Value("${mqtt.client.password}")
    private String password;

    @Value("${mqtt.client.qos}")
    private int qos;

    @Value("${mqtt.topic.connectivity}")
    private String connectivityTopic;

    @Value("${mqtt.topic.lwt}")
    private String lwtTopic;

    @Value("${mqtt.broker.ssl.key-store}")
    private String keyStorePath;

    @Value("${mqtt.broker.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${mqtt.broker.ssl.trust-store}")
    private String trustStorePath;

    @Value("${mqtt.broker.ssl.trust-store-password}")
    private String trustStorePassword;

    private MqttClient mqttClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Delay to let broker start first
        scheduler.schedule(this::connect, 3, TimeUnit.SECONDS);
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
            options.setSocketFactory(createSslSocketFactory());

            mqttClient.connect(options);
            log.info("PACS MQTT client connected to broker at {}", brokerUrl);

            // Subscribe to topics from EdgeConnector
            mqttClient.subscribe(connectivityTopic, qos);
            mqttClient.subscribe(lwtTopic, qos);
            log.info("Subscribed to topics: {} and {}", connectivityTopic, lwtTopic);

        } catch (Exception e) {
            log.error("PACS MQTT client connection failed: {}", e.getMessage(), e);
            scheduler.schedule(this::connect, 10, TimeUnit.SECONDS);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.info("PACS received message on [{}]: {}", topic, payload);

        try {
            JsonNode json = objectMapper.readTree(payload);
            String status = json.has("status") ? json.get("status").asText() : "unknown";
            String reason = json.has("reason") ? json.get("reason").asText() : null;
            String clientId = json.has("clientId") ? json.get("clientId").asText() : "EdgeConnector";

            boolean isLwt = topic.equals(lwtTopic);
            connectivityStatusService.recordConnectivityEvent(clientId, status,
                    isLwt ? "lwt" : "mqtt_status", reason, topic, payload);

        } catch (Exception e) {
            log.error("Failed to process message on topic {}: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("PACS MQTT client connection lost: {}", cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("{} PACS MQTT client: {}", reconnect ? "Reconnected" : "Connected", serverURI);
        try {
            mqttClient.subscribe(connectivityTopic, qos);
            mqttClient.subscribe(lwtTopic, qos);
        } catch (MqttException e) {
            log.error("Re-subscribe failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            log.error("Shutdown error: {}", e.getMessage());
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
