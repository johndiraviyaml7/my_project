package com.edge.connector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Configuration
public class HttpClientConfig {

    @Value("${mqtt.ssl.key-store}")
    private String keyStorePath;

    @Value("${mqtt.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${mqtt.ssl.trust-store}")
    private String trustStorePath;

    @Value("${mqtt.ssl.trust-store-password}")
    private String trustStorePassword;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        // Load KeyStore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream ks = resolveResource(keyStorePath)) {
            keyStore.load(ks, keyStorePassword.toCharArray());
        }

        // Load TrustStore
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

        // Build Java 21 HttpClient
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // RestTemplate doesn’t directly support java.net.http.HttpClient.
        // For simplicity, use SimpleClientHttpRequestFactory (uses JDK HttpURLConnection).
        // If you want full java.net.http.HttpClient integration, consider WebClient instead.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(30_000);

        return new RestTemplate(factory);
    }

    private InputStream resolveResource(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            return getClass().getClassLoader().getResourceAsStream(path.substring("classpath:".length()));
        }
        return new java.io.FileInputStream(path);
    }
}