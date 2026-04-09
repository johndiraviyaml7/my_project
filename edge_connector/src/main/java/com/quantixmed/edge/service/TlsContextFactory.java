package com.quantixmed.edge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * Builds a single TLSv1.3 SSLContext used by both the Paho MQTT client
 * and the HttpClient that calls PAS Connector over HTTPS.  Same
 * client.p12 + truststore.jks on both code paths.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TlsContextFactory {

    @Value("${edge.tls.keystore}")          private String keystorePath;
    @Value("${edge.tls.keystore-password}") private String keystorePassword;
    @Value("${edge.tls.truststore}")          private String truststorePath;
    @Value("${edge.tls.truststore-password}") private String truststorePassword;
    @Value("${edge.tls.protocol}")            private String protocol;

    public SSLContext buildContext() throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(keystorePath)) {
            keys.load(in, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keys, keystorePassword.toCharArray());

        KeyStore trust = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(truststorePath)) {
            trust.load(in, truststorePassword.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);

        SSLContext ctx = SSLContext.getInstance(protocol);
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        log.info("TLS context built ({}) — keystore={} truststore={}",
                protocol, keystorePath, truststorePath);
        return ctx;
    }
}
