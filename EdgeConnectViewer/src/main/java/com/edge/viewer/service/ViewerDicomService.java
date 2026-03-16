package com.edge.viewer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewerDicomService {

    private final RestTemplate restTemplate;

    @Value("${edge.connector.url}")
    private String edgeConnectorUrl;

    /**
     * Upload a DICOM file to EdgeConnector's upload API endpoint.
     * EdgeConnector handles the actual forwarding to PACSConnector.
     */
    public void uploadDicom(File dicomFile, Consumer<String> onSuccess, Consumer<String> onError) {
        new Thread(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new FileSystemResource(dicomFile));

                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                String uploadUrl = edgeConnectorUrl + "/api/dicom/upload";

                ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Upload succeeded: {}", dicomFile.getName());
                    onSuccess.accept("Uploaded successfully: " + dicomFile.getName());
                } else {
                    String msg = "Upload failed with status: " + response.getStatusCode();
                    log.warn(msg);
                    onError.accept(msg);
                }
            } catch (Exception e) {
                log.error("Upload error: {}", e.getMessage(), e);
                onError.accept("Upload error: " + e.getMessage());
            }
        }, "dicom-upload-thread").start();
    }

    public String getEdgeConnectorStatus() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    edgeConnectorUrl + "/api/status", String.class);
            return response.getBody();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
