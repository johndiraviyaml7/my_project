package com.pacs.connector.config;

import com.pacs.connector.mqtt.MqttAuthenticator;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttBrokerConfig {

    private final MqttAuthenticator mqttAuthenticator;

    @Value("${mqtt.broker.port:1883}")
    private int brokerPort;

    @Value("${mqtt.broker.ssl-port:8884}")
    private int sslPort;

    @Value("${mqtt.broker.wss-port:9001}")
    private int wssPort;

    @Value("${mqtt.broker.host:0.0.0.0}")
    private String brokerHost;

    @Value("${mqtt.broker.data-dir:./mqtt-data}")
    private String dataDir;

    @Value("${mqtt.broker.ssl.key-store}")
    private String keyStorePath;

    @Value("${mqtt.broker.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${mqtt.broker.ssl.trust-store}")
    private String trustStorePath;

    @Value("${mqtt.broker.ssl.trust-store-password}")
    private String trustStorePassword;

    private Server mqttBroker;

    @Bean(destroyMethod = "")
    public Server mqttBrokerServer() throws IOException {
        mqttBroker = new Server();

        Properties props = new Properties();
        props.setProperty(IConfig.HOST_PROPERTY_NAME, brokerHost);
        props.setProperty(IConfig.PORT_PROPERTY_NAME, String.valueOf(brokerPort));
        props.setProperty(IConfig.DATA_PATH_PROPERTY_NAME, dataDir);
        props.setProperty(IConfig.PERSISTENT_QUEUE_TYPE_PROPERTY_NAME, "H2");

        // Enable SSL/WSS
        props.setProperty(IConfig.SSL_PORT_PROPERTY_NAME, String.valueOf(sslPort));
        props.setProperty(IConfig.WSS_PORT_PROPERTY_NAME, String.valueOf(wssPort));
        props.setProperty(IConfig.JKS_PATH_PROPERTY_NAME, resolveResourcePath(keyStorePath));
        props.setProperty(IConfig.KEY_STORE_PASSWORD_PROPERTY_NAME, keyStorePassword);
        props.setProperty(IConfig.KEY_MANAGER_PASSWORD_PROPERTY_NAME, keyStorePassword);

        // Require client certificate authentication
       // props.setProperty(IConfig.NEED_CLIENT_AUTH, "true");

        // Allow anonymous for internal services
        props.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "false");

        // Session expiry configuration - prevents "session without expiry instant" errors
        props.setProperty(IConfig.SESSION_QUEUE_SIZE, "1024");
        props.setProperty("persistent_client_expiration", "24h");

        IConfig brokerConfig = new MemoryConfig(props);
        mqttBroker.startServer(brokerConfig, Collections.emptyList(), null, mqttAuthenticator, null);
        log.info("Embedded MQTT broker started on port {} (plain), {} (SSL), {} (WSS) with authentication enabled",
                brokerPort, sslPort, wssPort);

        return mqttBroker;
    }

    @PreDestroy
    public void stopBroker() {
        if (mqttBroker != null) {
            mqttBroker.stopServer();
            log.info("Embedded MQTT broker stopped");
        }
    }

    private String resolveResourcePath(String path) {
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            var url = getClass().getClassLoader().getResource(resource);
            return url != null ? url.getPath() : path;
        }
        return path;
    }
}
