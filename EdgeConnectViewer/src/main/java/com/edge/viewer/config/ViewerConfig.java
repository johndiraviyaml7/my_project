package com.edge.viewer.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;

@Slf4j
@Configuration
public class ViewerConfig {

    @Value("${ssl.key-store}")
    private String keyStorePath;

    @Value("${ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${ssl.trust-store}")
    private String trustStorePath;

    @Value("${ssl.trust-store-password}")
    private String trustStorePassword;

    @Bean
    public RestTemplate restTemplate() {
        try {
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

            SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier((hostname, session) -> true)
                    .build();

            log.warn("SSL hostname verification is DISABLED - for development use only!");

            HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();

            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            log.info("SSL-enabled RestTemplate created for EdgeConnector communication");
            return new RestTemplate(factory);

        } catch (Exception e) {
            log.error("Failed to create SSL RestTemplate: {}", e.getMessage(), e);
            log.warn("Falling back to non-SSL RestTemplate - HTTPS calls will fail!");
            return new RestTemplate();
        }
    }

    private InputStream resolveResource(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            return getClass().getClassLoader().getResourceAsStream(path.substring("classpath:".length()));
        }
        return new java.io.FileInputStream(path);
    }
}
