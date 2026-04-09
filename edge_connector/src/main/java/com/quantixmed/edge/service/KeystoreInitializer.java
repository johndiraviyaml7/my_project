package com.quantixmed.edge.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * At boot time, if certs/client.p12 and certs/truststore.jks are missing
 * but the PEM files (ca.crt, client.crt, client.key) are present, we build
 * them from the PEMs with the configured password.
 *
 * This means the Edge Connector distribution can ship as just the PEM
 * files (human-inspectable, not password-protected) and the first run
 * materialises the keystores the Spring SSL stack needs.
 */
@Component
@Slf4j
public class KeystoreInitializer {

    @Value("${edge.tls.keystore}")          private String keystorePath;
    @Value("${edge.tls.keystore-password}") private String keystorePassword;
    @Value("${edge.tls.truststore}")          private String truststorePath;
    @Value("${edge.tls.truststore-password}") private String truststorePassword;

    @PostConstruct
    public void ensureKeystores() {
        try {
            Path ks = Paths.get(keystorePath);
            Path ts = Paths.get(truststorePath);
            Path certsDir = ks.getParent() != null ? ks.getParent() : Paths.get("certs");
            Path caPem     = certsDir.resolve("ca.crt");
            Path clientCrt = certsDir.resolve("client.crt");
            Path clientKey = certsDir.resolve("client.key");

            if (!Files.exists(caPem)) {
                log.warn("ca.crt not found in {} — skipping keystore materialisation", certsDir);
                return;
            }

            // Build truststore if missing
            if (!Files.exists(ts)) {
                KeyStore trust = KeyStore.getInstance("JKS");
                trust.load(null, null);
                trust.setCertificateEntry("rootcamed", readCert(caPem));
                try (FileOutputStream out = new FileOutputStream(ts.toFile())) {
                    trust.store(out, truststorePassword.toCharArray());
                }
                log.info("Materialised truststore: {}", ts);
            }

            // Build client keystore if missing
            if (!Files.exists(ks) && Files.exists(clientCrt) && Files.exists(clientKey)) {
                KeyStore keys = KeyStore.getInstance("PKCS12");
                keys.load(null, null);
                PrivateKey pk = readPrivateKey(clientKey);
                X509Certificate leaf = readCert(clientCrt);
                X509Certificate ca   = readCert(caPem);
                keys.setKeyEntry("medclient",
                        pk,
                        keystorePassword.toCharArray(),
                        new Certificate[] { leaf, ca });
                try (FileOutputStream out = new FileOutputStream(ks.toFile())) {
                    keys.store(out, keystorePassword.toCharArray());
                }
                log.info("Materialised client keystore: {}", ks);
            }
        } catch (Exception e) {
            log.error("Keystore materialisation failed: {}", e.getMessage(), e);
        }
    }

    private X509Certificate readCert(Path pem) throws Exception {
        try (FileInputStream in = new FileInputStream(pem.toFile())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private PrivateKey readPrivateKey(Path pem) throws Exception {
        String raw = Files.readString(pem)
                .replaceAll("-----BEGIN (?:RSA |EC )?PRIVATE KEY-----", "")
                .replaceAll("-----END (?:RSA |EC )?PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(raw);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
    }
}
