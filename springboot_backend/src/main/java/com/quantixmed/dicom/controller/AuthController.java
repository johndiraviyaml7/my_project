package com.quantixmed.dicom.controller;

import com.quantixmed.dicom.dto.DicomDtos.LoginRequest;
import com.quantixmed.dicom.dto.DicomDtos.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@CrossOrigin(origins = "*")
public class AuthController {

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT token")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        // Demo authentication — replace with real JWT + UserDetailsService in production
        boolean valid =
                ("admin".equals(req.getUsername()) && "admin".equals(req.getPassword())) ||
                ("doctor".equals(req.getUsername()) && "doctor".equals(req.getPassword())) ||
                ("admin@quantixmed.com".equals(req.getUsername()) && "Admin@123".equals(req.getPassword()));

        if (valid) {
            String displayName = req.getUsername().contains("@")
                    ? "Dr. John Conor"
                    : "Dr. " + capitalize(req.getUsername());
            return ResponseEntity.ok(LoginResponse.builder()
                    .token("demo-jwt-" + req.getUsername().replaceAll("[^a-zA-Z0-9]", "-"))
                    .tokenType("Bearer")
                    .username(req.getUsername())
                    .name(displayName)
                    .expiresIn(86400000L)
                    .build());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
