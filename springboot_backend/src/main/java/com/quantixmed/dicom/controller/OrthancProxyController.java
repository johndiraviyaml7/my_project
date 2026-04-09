package com.quantixmed.dicom.controller;

import com.quantixmed.dicom.dto.DicomDtos.OrthancStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Orthanc Reverse Proxy — allows OHIF Viewer running in the browser to
 * reach Orthanc's DICOMweb endpoints (WADO-RS / STOW-RS / QIDO-RS) through
 * Spring Boot instead of calling Orthanc directly.
 *
 * OHIF config: wadoRsRoot = "http://localhost:8080/orthanc"
 *
 * All requests matching /orthanc/** are forwarded to Orthanc at
 * ${app.orthanc.url} with Basic auth injected by the server.
 */
@RestController
@RequestMapping("/orthanc")
@Slf4j
@Tag(name = "Orthanc Proxy")
@CrossOrigin(origins = "*")
public class OrthancProxyController {

    @Value("${app.orthanc.url:http://localhost:8042}")
    private String orthancUrl;

    @Value("${app.orthanc.username:admin}")
    private String orthancUser;

    @Value("${app.orthanc.password:admin}")
    private String orthancPassword;

    @Value("${app.ohif.viewer-url:http://localhost:3000}")
    private String ohifViewerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Status ──────────────────────────────────────────────────────────────

    @GetMapping("/status")
    @Operation(summary = "Check Orthanc connectivity and return config for OHIF")
    public ResponseEntity<OrthancStatusDto> status() {
        try {
            HttpHeaders headers = basicAuthHeaders();
            ResponseEntity<Map> r = restTemplate.exchange(
                    orthancUrl + "/system", HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);

            Integer studyCount = null;
            try {
                ResponseEntity<List> studies = restTemplate.exchange(
                        orthancUrl + "/studies", HttpMethod.GET,
                        new HttpEntity<>(headers), List.class);
                if (studies.getBody() != null) studyCount = studies.getBody().size();
            } catch (Exception ignored) {}

            return ResponseEntity.ok(OrthancStatusDto.builder()
                    .available(true)
                    .url(orthancUrl)
                    .dicomWebRoot(orthancUrl + "/dicom-web")
                    .ohifViewerBase(ohifViewerUrl)
                    .studyCount(studyCount)
                    .build());
        } catch (Exception e) {
            log.warn("Orthanc not reachable: {}", e.getMessage());
            return ResponseEntity.ok(OrthancStatusDto.builder()
                    .available(false)
                    .url(orthancUrl)
                    .dicomWebRoot(orthancUrl + "/dicom-web")
                    .ohifViewerBase(ohifViewerUrl)
                    .build());
        }
    }

    // ── OHIF viewer URL builder ──────────────────────────────────────────────

    @GetMapping("/viewer-url/{orthancStudyId}")
    @Operation(summary = "Get OHIF Viewer URL for a study (using Orthanc study ID)")
    public ResponseEntity<Map<String, String>> viewerUrl(@PathVariable String orthancStudyId) {
        // Resolve study instance UID from Orthanc
        try {
            HttpEntity<Void> req = new HttpEntity<>(basicAuthHeaders());
            ResponseEntity<Map> meta = restTemplate.exchange(
                    orthancUrl + "/studies/" + orthancStudyId,
                    HttpMethod.GET, req, Map.class);
            Map<?, ?> main = (Map<?, ?>) meta.getBody().get("MainDicomTags");
            String studyUid = main != null ? (String) main.get("StudyInstanceUID") : orthancStudyId;
            String wadoRoot = orthancUrl + "/dicom-web";
            String ohifUrl  = ohifViewerUrl + "/viewer?StudyInstanceUIDs=" + studyUid
                    + "&wadoRsRoot=" + wadoRoot;
            return ResponseEntity.ok(Map.of(
                    "ohifViewerUrl",     ohifUrl,
                    "studyInstanceUid",  studyUid,
                    "orthancStudyId",    orthancStudyId,
                    "wadoRsRoot",        wadoRoot
            ));
        } catch (Exception e) {
            log.warn("Cannot resolve study UID for {}: {}", orthancStudyId, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    // ── DICOMweb reverse proxy ───────────────────────────────────────────────

    @RequestMapping(value = "/dicom-web/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE})
    @Operation(summary = "Reverse proxy to Orthanc DICOMweb (WADO-RS/QIDO-RS/STOW-RS)")
    public ResponseEntity<byte[]> proxyDicomWeb(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return proxy(request, body, "/orthanc/dicom-web", "/dicom-web");
    }

    @RequestMapping(value = "/wado/**",
            method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Reverse proxy to Orthanc WADO endpoint")
    public ResponseEntity<byte[]> proxyWado(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return proxy(request, body, "/orthanc/wado", "/wado");
    }

    // ── Generic proxy helper ─────────────────────────────────────────────────

    private ResponseEntity<byte[]> proxy(HttpServletRequest request, byte[] body,
                                          String stripPrefix, String orthancPath) {
        try {
            String requestUri = request.getRequestURI();
            String tail = requestUri.substring(requestUri.indexOf(stripPrefix) + stripPrefix.length());
            String query = request.getQueryString();
            String targetUrl = orthancUrl + orthancPath + tail + (query != null ? "?" + query : "");

            HttpHeaders headers = basicAuthHeaders();
            String contentType = request.getContentType();
            if (contentType != null) headers.set(HttpHeaders.CONTENT_TYPE, contentType);

            // Forward relevant request headers
            Enumeration<String> names = request.getHeaderNames();
            while (names != null && names.hasMoreElements()) {
                String name = names.nextElement();
                if (!name.equalsIgnoreCase("authorization") &&
                    !name.equalsIgnoreCase("host") &&
                    !name.equalsIgnoreCase("content-length")) {
                    headers.set(name, request.getHeader(name));
                }
            }

            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            HttpEntity<byte[]> entity = (body != null && body.length > 0)
                    ? new HttpEntity<>(body, headers)
                    : new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(targetUrl), method, entity, byte[].class);

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setAccessControlAllowOrigin("*");
            responseHeaders.setAccessControlAllowHeaders(Collections.singletonList("*"));
            if (response.getHeaders().getContentType() != null) {
                responseHeaders.setContentType(response.getHeaders().getContentType());
            }

            return ResponseEntity.status(response.getStatusCode())
                    .headers(responseHeaders)
                    .body(response.getBody());

        } catch (Exception e) {
            log.error("Orthanc proxy error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Orthanc proxy error: " + e.getMessage()).getBytes());
        }
    }

    private HttpHeaders basicAuthHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBasicAuth(orthancUser, orthancPassword);
        return h;
    }
}
