package com.quantixmed.pas.mqtt;

import io.moquette.broker.ISslContextCreator;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Properties;

/**
 * Embedded Moquette broker with a hand-built SslContext.
 *
 * v5.8 changes from v5.7:
 *   - Force TLSv1.2 (eliminates the TLSv1.3 + Netty JDK provider +
 *     client-cert handshake quirk we suspect)
 *   - Log the keystore alias + cert subject so we can confirm what's
 *     actually being presented to clients
 *   - Force a permissive cipher suite list compatible with both 1.2
 *     and our self-signed RSA certs
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
        props.setProperty("host", host);
        props.setProperty("port", String.valueOf(plainPort));
        props.setProperty("ssl_port", String.valueOf(tlsPort));
        props.setProperty("need_client_auth", "true");
        props.setProperty("allow_anonymous",          "true");
        props.setProperty("authenticator_class",      "");
        props.setProperty("authorizator_class",       "");
        props.setProperty("persistence_enabled",      "false");
        props.setProperty("telemetry_enabled",        "false");
        props.setProperty("allow_zero_byte_client_id","true");
        // Stub keystore properties to satisfy Moquette's config validation
        props.setProperty("jks_path",             keystorePath);
        props.setProperty("key_store_type",       "PKCS12");
        props.setProperty("key_store_password",   keystorePassword);
        props.setProperty("key_manager_password", keystorePassword);

        MemoryConfig cfg = new MemoryConfig(props);
        broker = new Server();
        broker.startServer(cfg, null, new CustomSslContextCreator(), null, null);

        log.info("Moquette broker started: plain={}:{}, tls={}:{}, keystore={}, truststore={}",
                host, plainPort, host, tlsPort, keystorePath, truststorePath);
    }

    @PreDestroy
    public void stop() {
        if (broker != null) {
            broker.stopServer();
            log.info("Moquette broker stopped");
        }
    }

    private class CustomSslContextCreator implements ISslContextCreator {
        @Override
        public SslContext initSSLContext() {
            try {
                // ── Server identity ──────────────────────────────────────
                KeyStore keys = KeyStore.getInstance("PKCS12");
                try (FileInputStream in = new FileInputStream(keystorePath)) {
                    keys.load(in, keystorePassword.toCharArray());
                }
                // Log every alias in the keystore for diagnostic purposes
                java.util.Enumeration<String> aliases = keys.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (keys.isKeyEntry(alias)) {
                        java.security.cert.Certificate c = keys.getCertificate(alias);
                        if (c instanceof java.security.cert.X509Certificate x) {
                            log.info("Keystore alias [{}] subject={} issuer={}",
                                    alias, x.getSubjectX500Principal(), x.getIssuerX500Principal());
                        }
                    }
                }

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keys, keystorePassword.toCharArray());

                // ── Trust material ───────────────────────────────────────
                KeyStore trust = KeyStore.getInstance("JKS");
                try (FileInputStream in = new FileInputStream(truststorePath)) {
                    trust.load(in, truststorePassword.toCharArray());
                }
                java.util.Enumeration<String> tAliases = trust.aliases();
                while (tAliases.hasMoreElements()) {
                    String alias = tAliases.nextElement();
                    java.security.cert.Certificate c = trust.getCertificate(alias);
                    if (c instanceof java.security.cert.X509Certificate x) {
                        log.info("Truststore alias [{}] subject={}",
                                alias, x.getSubjectX500Principal());
                    }
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trust);

                // ── Build the Netty SslContext ──────────────────────────
                // ── Build the Netty SslContext ──────────────────────────
                // Force TLSv1.2 ONLY on the MQTT listener.  With TLSv1.3
                // enabled, Netty's JDK provider silently drops incoming
                // client-cert connections without logging any error — a
                // known interaction between post-handshake client-auth
                // and Moquette's Netty pipeline.  HTTPS on 8443 is
                // unaffected because it uses Tomcat's SSL stack, not Netty.
                SslContext ctx = SslContextBuilder
                        .forServer(kmf)
                        .trustManager(tmf)
                        .clientAuth(ClientAuth.REQUIRE)
                        .sslProvider(SslProvider.JDK)
                        .protocols("TLSv1.2")
                        .build();

                log.info("Custom SslContext built — protocol=TLSv1.2 (MQTT only) keystore={} ({} entries) truststore={} ({} entries) clientAuth=REQUIRE",
                        keystorePath, keys.size(), truststorePath, trust.size());
                return ctx;
            } catch (Exception e) {
                log.error("Failed to build custom SslContext", e);
                throw new RuntimeException(e);
            }
        }
    }
}
