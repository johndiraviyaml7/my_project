package com.edge.connector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.edge.connector.controller.EdgeConnectorController;

import jakarta.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DicomUploadService {

    private RestTemplate restTemplate;
    private static final Logger log = LoggerFactory.getLogger(EdgeConnectorController.class);

    @Value("${mqtt.ssl.key-store}")
    private String keyStorePath;

    @Value("${mqtt.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${mqtt.ssl.trust-store}")
    private String trustStorePath;

    @Value("${mqtt.ssl.trust-store-password}")
    private String trustStorePassword;


    @Value("${pacs.upload.url}")
    private String uploadUrl;

    @Value("${dicom.watch.folder}")
    private String watchFolder;

    @Value("${dicom.processed.folder}")
    private String processedFolder;

    @Value("${dicom.failed.folder}")
    private String failedFolder;

    @PostConstruct
    public void init() {
        // Ensure directories exist
        createDirectoryIfNotExists(watchFolder);
        createDirectoryIfNotExists(processedFolder);
        createDirectoryIfNotExists(failedFolder);
        
        // Initialize SSL-enabled RestTemplate
        try {
            restTemplate = createSslRestTemplate();
            log.info("DicomUploadService initialized with SSL. Watching folder: {}", watchFolder);
        } catch (Exception e) {
            log.error("Failed to initialize SSL RestTemplate: {}", e.getMessage(), e);
            restTemplate = new RestTemplate();
            log.warn("Falling back to non-SSL RestTemplate");
        }
    }

    private RestTemplate createSslRestTemplate() throws Exception {
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

        // Disable hostname verification for development
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
        log.info("TLS 1.3 enabled for REST client to PACS");
        return new RestTemplate(factory);
    }

    private InputStream resolveResource(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            return getClass().getClassLoader().getResourceAsStream(path.substring("classpath:".length()));
        }
        return new java.io.FileInputStream(path);
    }

    @Scheduled(fixedDelayString = "${dicom.watch.interval-ms:5000}")
    public void scanAndUpload() {
        File folder = new File(watchFolder);
        File[] dicomFiles = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".dcm") || name.toLowerCase().endsWith(".dicom"));

        if (dicomFiles == null || dicomFiles.length == 0) {
            return;
        }

        log.debug("Found {} DICOM file(s) to process", dicomFiles.length);

        for (File file : dicomFiles) {
            processFile(file);
        }
    }

    public void processFile(File file) {
        log.info("Processing DICOM file: {}", file.getName());
        try {
            String response = uploadDicom(file);
            log.info("Successfully uploaded: {} -> {}", file.getName(), response);
            moveFile(file, processedFolder);
        } catch (Exception e) {
            log.error("Failed to upload DICOM file {}: {}", file.getName(), e.getMessage(), e);
            moveFile(file, failedFolder);
        }
    }

    private String uploadDicom(File file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        body.add("patientId", extractPatientId(file.getName()));
        body.add("sourceDevice", "EdgeConnector");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Upload failed with status: " + response.getStatusCode());
        }

        return response.getBody();
    }

    private void moveFile(File file, String destinationFolder) {
        try {
            Path source = file.toPath();
            Path destination = Paths.get(destinationFolder, file.getName());
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Moved {} to {}", file.getName(), destinationFolder);
        } catch (Exception e) {
            log.error("Failed to move file {}: {}", file.getName(), e.getMessage(), e);
        }
    }

    private String extractPatientId(String filename) {
        // Extract patient ID from filename convention: PATIENTID_STUDYDATE.dcm
        String[] parts = filename.replace(".dcm", "").replace(".dicom", "").split("_");
        return parts.length > 0 ? parts[0] : "UNKNOWN";
    }

    private void createDirectoryIfNotExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("Created directory: {}", path);
        }
    }
}
