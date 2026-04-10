package com.quantixmed.edge.service;

import com.quantixmed.edge.dto.EdgeDtos.RegisterForm;
import com.quantixmed.edge.dto.EdgeDtos.RegisterResult;
import com.quantixmed.edge.mqtt.EdgeMqttPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On application startup, check whether a prior registration was persisted.
 * If yes, re-register with PAS (to update {@code last_seen_at} and let the
 * server confirm we're still a known device) and then bring the MQTT
 * heartbeat loop back up — all without the user clicking anything.
 *
 * If PAS is temporarily unreachable we log a warning and keep the saved
 * registration; the user can click Register manually later, or the next
 * startup will retry.  We deliberately do NOT start MQTT when the re-register
 * fails, because PAS needs to have an up-to-date device row before the
 * heartbeats start landing or the dashboard will show "unknown device".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupAutoRegister {

    private final RegistrationStore store;
    private final PasClient pasClient;
    private final EdgeMqttPublisher mqtt;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        RegisterForm saved = store.load();
        if (saved == null) {
            log.info("No saved registration — waiting for user to register manually");
            return;
        }

        log.info("Auto-restoring registration for serial={}", saved.getSerialNumber());
        // Run in a background thread so slow PAS doesn't hold up the
        // Swing UI opening (the controller is already listening on :9090
        // by the time this event fires, but the UI-poll endpoint should
        // not wait for network I/O).
        new Thread(() -> {
            try {
                RegisterResult result = pasClient.register(saved);
                if (result.isSuccess()) {
                    log.info("Auto-registration succeeded — starting MQTT");
                    mqtt.connectAs(saved.getSerialNumber());
                } else {
                    log.warn("Auto-registration failed: {} — user can retry manually",
                            result.getMessage());
                }
            } catch (Exception e) {
                log.warn("Auto-registration threw: {} — user can retry manually",
                        e.getMessage());
            }
        }, "edge-auto-register").start();
    }
}
