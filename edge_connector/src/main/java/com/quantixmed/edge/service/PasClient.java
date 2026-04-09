package com.quantixmed.edge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantixmed.edge.dto.EdgeDtos.RegisterForm;
import com.quantixmed.edge.dto.EdgeDtos.RegisterResult;
import com.quantixmed.edge.dto.EdgeDtos.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * mTLS-authenticated HTTPS client for calling the PAS Connector.
 *
 * We use the JDK's HttpsURLConnection with a custom SSLContext (from
 * TlsContextFactory) rather than RestTemplate — RestTemplate's HttpClient
 * integration for mTLS + multipart uploads is significantly more
 * dependency-heavy and we don't need any of its features.
 *
 * Multipart upload body is built by hand per RFC 7578.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasClient {

    private final TlsContextFactory tlsFactory;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${edge.pas.base-url}")
    private String pasBaseUrl;

    private SSLContext sslContextCache;
    private SSLContext ssl() throws Exception {
        if (sslContextCache == null) sslContextCache = tlsFactory.buildContext();
        return sslContextCache;
    }

    public RegisterResult register(RegisterForm form) {
        try {
            String body = json.writeValueAsString(form);
            URL url = new URL(pasBaseUrl + "/api/pas/register");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(ssl().getSocketFactory());
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            try (var out = conn.getOutputStream()) {
                out.write(body.getBytes());
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = in != null ? new String(in.readAllBytes()) : "";
            if (code >= 200 && code < 300) {
                JsonNode node = json.readTree(resp);
                return RegisterResult.builder()
                        .success(true)
                        .message(node.path("message").asText("registered"))
                        .serialNumber(node.path("serialNumber").asText())
                        .deviceId(node.path("id").asText())
                        .build();
            } else {
                log.warn("Register HTTP {}: {}", code, resp);
                return RegisterResult.builder()
                        .success(false)
                        .message("HTTP " + code + ": " + resp)
                        .build();
            }
        } catch (Exception e) {
            log.error("Register failed", e);
            return RegisterResult.builder()
                    .success(false)
                    .message(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    public UploadResult uploadZip(String serialNumber, Path zipPath, String modality) {
        try {
            String boundary = "----edge" + System.currentTimeMillis();
            URL url = new URL(pasBaseUrl + "/api/pas/upload/" + serialNumber);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(ssl().getSocketFactory());
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setChunkedStreamingMode(64 * 1024);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(600_000);

            byte[] fileBytes = Files.readAllBytes(zipPath);
            String filename = zipPath.getFileName().toString();

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                // modality part
                if (modality != null && !modality.isBlank()) {
                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"modality\"\r\n\r\n");
                    out.writeBytes(modality + "\r\n");
                }
                // file part
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
                out.writeBytes("Content-Type: application/zip\r\n\r\n");
                out.write(fileBytes);
                out.writeBytes("\r\n--" + boundary + "--\r\n");
            }

            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = in != null ? new String(in.readAllBytes()) : "";
            if (code >= 200 && code < 300) {
                JsonNode node = json.readTree(resp);
                return UploadResult.builder()
                        .success(true)
                        .filename(filename)
                        .bytes(node.path("bytes").asLong(fileBytes.length))
                        .message("OK")
                        .build();
            } else {
                return UploadResult.builder()
                        .success(false)
                        .filename(filename)
                        .message("HTTP " + code + ": " + resp)
                        .build();
            }
        } catch (Exception e) {
            log.error("Upload failed", e);
            return UploadResult.builder()
                    .success(false)
                    .message(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
