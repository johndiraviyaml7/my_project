package com.edge.viewer;

import com.edge.viewer.ui.MainWindow;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;

@SpringBootApplication
public class EdgeConnectViewerApplication {

    public static void main(String[] args) {
        // Set System look and feel for Windows native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // fallback to default
        }

        // Start Spring context without web server (desktop app)
        ConfigurableApplicationContext context = new SpringApplicationBuilder(EdgeConnectViewerApplication.class)
                .headless(false)
                .run(args);

        // Launch Swing UI on EDT
        SwingUtilities.invokeLater(() -> {
            MainWindow mainWindow = context.getBean(MainWindow.class);
            mainWindow.show();
        });
    }
}
