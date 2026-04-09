package com.quantixmed.pas.controller;

import com.quantixmed.pas.dto.PasDtos.RegisterRequest;
import com.quantixmed.pas.dto.PasDtos.RegisterResponse;
import com.quantixmed.pas.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.cert.X509Certificate;

/**
 * Device registration endpoint.
 *
 * mTLS is enforced at the container level (server.ssl.client-auth=need).
 * By the time a request reaches this controller, Tomcat has already
 * verified that the caller presented a client certificate signed by
 * our truststore's root CA. We pull the cert from the servlet request
 * and stamp the CN into the log for auditing.
 */
@RestController
@RequestMapping("/api/pas")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController {

    private final DeviceService deviceService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req,
                                                      HttpServletRequest httpReq) {
        String callerCn = extractCallerCn(httpReq);
        log.info("Register request from CN={} for serial={}", callerCn, req.getSerialNumber());
        RegisterResponse resp = deviceService.register(req);
        return ResponseEntity.ok(resp);
    }

    private String extractCallerCn(HttpServletRequest req) {
        X509Certificate[] certs = (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certs == null || certs.length == 0) return "unknown";
        String dn = certs[0].getSubjectX500Principal().getName();
        // CN=MedClient, OU=MedTechAI, ...
        for (String part : dn.split(",")) {
            String p = part.trim();
            if (p.startsWith("CN=")) return p.substring(3);
        }
        return dn;
    }
}
