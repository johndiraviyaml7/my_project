package com.quantixmed.pas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 10 seconds, mark any "Connected" device whose last heartbeat is
 * older than the heartbeat timeout as "Disconnected".  Handles the
 * case where an Edge goes away without publishing LWT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleDeviceSweeper {

    private final DeviceService deviceService;

    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void sweep() {
        int flipped = deviceService.sweepStale();
        if (flipped > 0) {
            log.info("Sweep flipped {} device(s) to Disconnected", flipped);
        }
    }
}
