package com.edge.viewer.ui;

import com.edge.viewer.service.ViewerDicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class MainWindow {

    private final ViewerDicomService dicomService;

    private JFrame frame;
    private JTextArea logArea;
    private JButton uploadButton;
    private JLabel selectedFileLabel;
    private File selectedDicomFile;
    private JProgressBar uploadProgressBar;
    private JLabel connectorStatusLabel;
    private JLabel mqttStatusLabel;

    private static final Color COLOR_CONNECTED = new Color(0, 153, 51);
    private static final Color COLOR_DISCONNECTED = new Color(204, 0, 0);
    private static final Color COLOR_BG = new Color(245, 245, 250);
    private static final Color COLOR_ACCENT = new Color(0, 102, 204);

    public void show() {
        frame = new JFrame("EdgeConnect Viewer");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(750, 600);
        frame.setMinimumSize(new Dimension(650, 500));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(COLOR_BG);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onShutdown();
            }
        });

        frame.setLayout(new BorderLayout(10, 10));
        frame.add(buildTitlePanel(), BorderLayout.NORTH);
        frame.add(buildMainPanel(), BorderLayout.CENTER);
        frame.add(buildStatusBar(), BorderLayout.SOUTH);

        // Initial status check
        refreshConnectorStatus();

        frame.setVisible(true);
    }

    private JPanel buildTitlePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_ACCENT);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("EdgeConnect Viewer");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        panel.add(title, BorderLayout.WEST);

        JLabel version = new JLabel("v1.0.0");
        version.setForeground(new Color(200, 220, 255));
        version.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        panel.add(version, BorderLayout.EAST);

        return panel;
    }

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(COLOR_BG);
        panel.setBorder(new EmptyBorder(10, 16, 10, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 0);
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        // EdgeConnector Status Panel
        gbc.gridy = 0;
        panel.add(buildStatusPanel(), gbc);

        // DICOM Upload Panel
        gbc.gridy = 1;
        panel.add(buildDicomUploadPanel(), gbc);

        // Log Panel (fills remaining space)
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(buildLogPanel(), gbc);

        return panel;
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(createTitledBorder("EdgeConnector Status"));
        panel.setBackground(Color.WHITE);

        // EdgeConnector API status row
        JPanel connectorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        connectorRow.setBackground(Color.WHITE);
        connectorRow.add(new JLabel("EdgeConnector API:"));
        connectorStatusLabel = new JLabel("Checking...");
        connectorStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JButton refreshBtn = new JButton("Refresh Status");
        refreshBtn.addActionListener(e -> refreshConnectorStatus());
        connectorRow.add(connectorStatusLabel);
        connectorRow.add(Box.createHorizontalStrut(10));
        connectorRow.add(refreshBtn);

        // MQTT status row (from EdgeConnector)
        JPanel mqttRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        mqttRow.setBackground(Color.WHITE);
        mqttRow.add(new JLabel("MQTT Broker (via EdgeConnector):"));
        mqttStatusLabel = new JLabel("Unknown");
        mqttStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        mqttRow.add(mqttStatusLabel);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.add(connectorRow);
        content.add(mqttRow);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDicomUploadPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(createTitledBorder("DICOM Image Upload"));
        panel.setBackground(Color.WHITE);

        // File selection row
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        fileRow.setBackground(Color.WHITE);

        selectedFileLabel = new JLabel("No file selected");
        selectedFileLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        selectedFileLabel.setForeground(Color.GRAY);
        selectedFileLabel.setPreferredSize(new Dimension(400, 25));
        selectedFileLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> onBrowseDicom());

        fileRow.add(selectedFileLabel);
        fileRow.add(browseButton);

        // Upload row
        JPanel uploadRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        uploadRow.setBackground(Color.WHITE);

        uploadProgressBar = new JProgressBar();
        uploadProgressBar.setStringPainted(true);
        uploadProgressBar.setString("Ready");
        uploadProgressBar.setPreferredSize(new Dimension(400, 22));

        uploadButton = new JButton("Upload DICOM");
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(e -> onUploadDicom());

        uploadRow.add(uploadProgressBar);
        uploadRow.add(uploadButton);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.add(fileRow);
        content.add(Box.createVerticalStrut(6));
        content.add(uploadRow);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(createTitledBorder("Activity Log"));
        panel.setBackground(Color.WHITE);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 230, 100));
        logArea.setMargin(new Insets(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(0, 150));

        JButton clearBtn = new JButton("Clear Log");
        clearBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        clearBtn.addActionListener(e -> logArea.setText(""));

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(clearBtn, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bar.setBackground(new Color(230, 230, 240));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        JLabel status = new JLabel("EdgeConnect Viewer  |  Ready");
        status.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        status.setForeground(Color.DARK_GRAY);
        bar.add(status);
        return bar;
    }

    // ---- Actions ----

    private void onBrowseDicom() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select DICOM File");
        chooser.setFileFilter(new FileNameExtensionFilter("DICOM Files (*.dcm, *.dicom)", "dcm", "dicom"));
        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedDicomFile = chooser.getSelectedFile();
            selectedFileLabel.setText(selectedDicomFile.getName() +
                    " (" + (selectedDicomFile.length() / 1024) + " KB)");
            selectedFileLabel.setForeground(Color.DARK_GRAY);
            selectedFileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            uploadButton.setEnabled(true);
            appendLog("[FILE] Selected: " + selectedDicomFile.getAbsolutePath());
        }
    }

    private void onUploadDicom() {
        if (selectedDicomFile == null) return;
        uploadButton.setEnabled(false);
        uploadProgressBar.setIndeterminate(true);
        uploadProgressBar.setString("Uploading...");
        appendLog("[UPLOAD] Uploading: " + selectedDicomFile.getName());

        dicomService.uploadDicom(
                selectedDicomFile,
                success -> SwingUtilities.invokeLater(() -> {
                    uploadProgressBar.setIndeterminate(false);
                    uploadProgressBar.setValue(100);
                    uploadProgressBar.setString("Upload Complete");
                    uploadButton.setEnabled(true);
                    appendLog("[SUCCESS] " + success);
                    JOptionPane.showMessageDialog(frame, success, "Upload Successful", JOptionPane.INFORMATION_MESSAGE);
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    uploadProgressBar.setIndeterminate(false);
                    uploadProgressBar.setValue(0);
                    uploadProgressBar.setString("Upload Failed");
                    uploadButton.setEnabled(true);
                    appendLog("[ERROR] " + error);
                    JOptionPane.showMessageDialog(frame, error, "Upload Failed", JOptionPane.ERROR_MESSAGE);
                })
        );
    }

    private void refreshConnectorStatus() {
        appendLog("[STATUS] Checking EdgeConnector status...");
        new Thread(() -> {
            String status = dicomService.getEdgeConnectorStatus();
            SwingUtilities.invokeLater(() -> {
                boolean isOnline = !status.contains("error");
                connectorStatusLabel.setText(isOnline ? "✓ Online" : "⚠ Offline");
                connectorStatusLabel.setForeground(isOnline ? COLOR_CONNECTED : COLOR_DISCONNECTED);
                
                // Update MQTT status from EdgeConnector response
                if (isOnline && status.contains("mqtt_connected")) {
                    boolean mqttConnected = status.contains("\"mqtt_connected\":true") || 
                                           status.contains("mqtt_connected=true");
                    mqttStatusLabel.setText(mqttConnected ? "✓ Connected" : "⚠ Disconnected");
                    mqttStatusLabel.setForeground(mqttConnected ? COLOR_CONNECTED : COLOR_DISCONNECTED);
                } else {
                    mqttStatusLabel.setText("Unknown");
                    mqttStatusLabel.setForeground(Color.GRAY);
                }
                
                appendLog("[STATUS] EdgeConnector: " + status);
            });
        }).start();
    }

    private void onShutdown() {
        int result = JOptionPane.showConfirmDialog(frame,
                "Are you sure you want to close EdgeConnect Viewer?",
                "Confirm Exit", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            appendLog("[SHUTDOWN] Viewer closing...");
            frame.dispose();
            System.exit(0);
        }
    }

    private void appendLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + timestamp + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(6, 14, 6, 14));
        return btn;
    }

    private Border createTitledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 210)),
                title,
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                COLOR_ACCENT
        );
        return BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(4, 8, 8, 8));
    }
}
