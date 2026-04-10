package com.quantixmed.edge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantixmed.edge.dto.EdgeDtos.RegisterForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Persists the last-successful registration to a JSON file next to the
 * jar (working-directory-relative: {@code registration.json}).  On
 * subsequent launches the Edge Connector reads this file, auto-populates
 * the Swing form, and automatically reconnects to the MQTT broker without
 * requiring the user to click Register again.
 *
 * The file is written atomically — we write to a .tmp sibling first and
 * then do an atomic rename, so a crash mid-write can't leave a truncated
 * JSON file behind.
 *
 * The file contains only the form fields (serial, device name, etc.).
 * Certificates remain in certs/ and are loaded separately — that folder
 * already persists across restarts from the install folder layout.
 */
@Service
@Slf4j
public class RegistrationStore {

    private static final Path FILE     = Paths.get("registration.json");
    private static final Path TMP_FILE = Paths.get("registration.json.tmp");

    private final ObjectMapper json = new ObjectMapper();

    /** Returns the saved form, or null if no prior registration exists. */
    public RegisterForm load() {
        if (!Files.exists(FILE)) return null;
        try {
            byte[] bytes = Files.readAllBytes(FILE);
            RegisterForm form = json.readValue(bytes, RegisterForm.class);
            log.info("Loaded previous registration for serial={} from {}",
                    form.getSerialNumber(), FILE.toAbsolutePath());
            return form;
        } catch (Exception e) {
            log.warn("Could not read {}: {} — starting fresh",
                    FILE.toAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /** Persists the given form.  Called after a successful registration. */
    public void save(RegisterForm form) {
        try {
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(form);
            Files.write(TMP_FILE, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(TMP_FILE, FILE,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            log.info("Saved registration for serial={} to {}",
                    form.getSerialNumber(), FILE.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to persist registration to {}: {}",
                    FILE.toAbsolutePath(), e.getMessage(), e);
        }
    }

    /** Deletes the persisted registration (e.g., on an explicit "Unregister"). */
    public void clear() {
        try {
            Files.deleteIfExists(FILE);
            log.info("Cleared persisted registration");
        } catch (Exception e) {
            log.warn("Could not delete {}: {}", FILE, e.getMessage());
        }
    }
}
