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
 * Builds an SSLContext used by both the Paho MQTT client and the
 * HttpClient that calls PAS Connector over HTTPS.  Same client.p12 +
 * truststore.jks on both code paths.  The protocol string in
 * application.properties is typically "TLS" (auto-negotiate 1.2/1.3)
 * but can be forced to a specific version for debugging.
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

    /** Generic HTTPS context — auto-negotiates whatever version the server offers. */
    public SSLContext buildContext() throws Exception {
        return buildContextInternal(protocol);
    }

    /** MQTT-specific context, forced to TLSv1.2 to work around the known
     *  Netty-JDK-provider-plus-client-cert bug on the broker side. */
    public SSLContext buildMqttContext() throws Exception {
        return buildContextInternal("TLSv1.2");
    }

    private SSLContext buildContextInternal(String requestedProtocol) throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(keystorePath)) {
            keys.load(in, keystorePassword.toCharArray());
        }
        // Diagnostic: log every keystore alias + cert subject
        java.util.Enumeration<String> aliases = keys.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keys.isKeyEntry(alias)) {
                java.security.cert.Certificate c = keys.getCertificate(alias);
                if (c instanceof java.security.cert.X509Certificate x) {
                    log.info("Edge keystore alias [{}] subject={} issuer={}",
                            alias, x.getSubjectX500Principal(), x.getIssuerX500Principal());
                }
            }
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keys, keystorePassword.toCharArray());

        KeyStore trust = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(truststorePath)) {
            trust.load(in, truststorePassword.toCharArray());
        }
        java.util.Enumeration<String> tAliases = trust.aliases();
        while (tAliases.hasMoreElements()) {
            String alias = tAliases.nextElement();
            java.security.cert.Certificate c = trust.getCertificate(alias);
            if (c instanceof java.security.cert.X509Certificate x) {
                log.info("Edge truststore alias [{}] subject={}",
                        alias, x.getSubjectX500Principal());
            }
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);

        SSLContext ctx = SSLContext.getInstance(requestedProtocol);
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        log.info("TLS context built ({}) — keystore={} ({} entries) truststore={} ({} entries)",
                requestedProtocol, keystorePath, keys.size(), truststorePath, trust.size());
        return ctx;
    }
}
