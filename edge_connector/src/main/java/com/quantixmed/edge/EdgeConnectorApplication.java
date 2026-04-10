package com.quantixmed.edge;

import com.quantixmed.edge.ui.EdgeMainWindow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;
import java.awt.*;

/**
 * Entry point for the Edge Connector.
 *
 *   java -jar edge-connector.jar            -> headless service
 *   java -jar edge-connector.jar --ui       -> boots service THEN opens Swing window
 *
 * The Swing UI is part of the same jar so there is one installable
 * artefact on Windows.  The UI talks to localhost:9090 — the embedded
 * Spring Boot REST server — rather than sharing the service objects
 * directly, to keep the UI and the REST API perfectly in sync.
 */
@SpringBootApplication
@EnableScheduling
public class EdgeConnectorApplication {

    public static void main(String[] args) {
        boolean wantUi = false;
        for (String a : args) if ("--ui".equalsIgnoreCase(a)) wantUi = true;

        // On headful Windows you typically always want the UI; honour the
        // "java.awt.headless" property if someone sets it explicitly.
        if (!wantUi && !GraphicsEnvironment.isHeadless()) {
            wantUi = true;
        }

        // Spring Boot defaults java.awt.headless=true, which would make
        // every Swing call throw HeadlessException.  Force it off BEFORE
        // SpringApplication.run() so the AWT toolkit initialises in
        // headful mode for the rest of the JVM's lifetime.
        System.setProperty("java.awt.headless", "false");

        SpringApplication app = new SpringApplication(EdgeConnectorApplication.class);
        app.setHeadless(false);
        ConfigurableApplicationContext ctx = app.run(args);

        if (wantUi) {
            SwingUtilities.invokeLater(() -> new EdgeMainWindow(ctx).setVisible(true));
        }
    }
}
