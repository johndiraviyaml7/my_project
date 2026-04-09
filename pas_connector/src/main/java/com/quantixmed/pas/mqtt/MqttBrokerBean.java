package com.quantixmed.pas.mqtt;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Starts an embedded Moquette MQTT broker.
 *
 * Two listeners:
 *   - Plain TCP on 1883     — used by the in-JVM self-subscriber
 *   - TLS      on 8883      — used by Edge Connectors across the network
 *
 * The broker uses the same server.p12 / truststore.jks as the HTTPS REST
 * API.  Property names below are the literal strings Moquette reads from
 * its Properties config (documented in moquette.conf reference). We do
 * NOT use the IConfig.* or BrokerConstants.* constants because they have
 * been renamed / moved between Moquette versions; the string values are
 * the stable contract.
 */
@Component
@Slf4j
public class MqttBrokerBean {

    @Value("${pas.mqtt.host}")               private String host;
    @Value("${pas.mqtt.port}")                private int tlsPort;
    @Value("${pas.mqtt.plain-port}")          private int plainPort;
    @Value("${pas.mqtt.keystore}")            private String keystorePath;
    @Value("${pas.mqtt.keystore-password}")   private String keystorePassword;
    @Value("${pas.mqtt.truststore}")          private String truststorePath;
    @Value("${pas.mqtt.truststore-password}") private String truststorePassword;

    private Server broker;

    @PostConstruct
    public void start() throws Exception {
        Properties props = new Properties();

        // ── Plain TCP listener ──────────────────────────────────────
        props.setProperty("host", host);
        props.setProperty("port", String.valueOf(plainPort));

        // ── TLS listener ────────────────────────────────────────────
        props.setProperty("ssl_port",             String.valueOf(tlsPort));
        props.setProperty("jks_path",             keystorePath);
        props.setProperty("key_store_type",       "PKCS12");
        props.setProperty("key_store_password",   keystorePassword);
        props.setProperty("key_manager_password", keystorePassword);
        props.setProperty("ssl_provider",         "JDK");

        // Require client certificate on the TLS listener
        props.setProperty("need_client_auth", "true");

        // Allow anonymous publishers — client cert IS the auth
        props.setProperty("allow_anonymous",     "true");
        props.setProperty("authenticator_class", "");
        props.setProperty("authorizator_class",  "");

        // Disable persistence — device connectivity state is ephemeral
        // and we track everything we care about in Postgres.
        props.setProperty("persistence_enabled", "false");
        // Telemetry off (no external calls home from an embedded broker)
        props.setProperty("telemetry_enabled", "false");
        // Allow clients with a zero-byte client id
        props.setProperty("allow_zero_byte_client_id", "true");

        MemoryConfig cfg = new MemoryConfig(props);
        broker = new Server();
        broker.startServer(cfg);

        log.info("Moquette broker started: plain={}:{}, tls={}:{}, keystore={}",
                host, plainPort, host, tlsPort, keystorePath);
    }

    @PreDestroy
    public void stop() {
        if (broker != null) {
            broker.stopServer();
            log.info("Moquette broker stopped");
        }
    }
}
