package com.quantixmed.edge.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * QuantixMed Edge Connector — Swing main window.
 *
 * Tabs:
 *   1. Register      — always enabled
 *   2. Connected     — enabled after /register succeeds; shows heartbeat count
 *                      and last error; polls /edge/status every 2s
 *   3. Upload        — enabled after registration; file chooser + upload
 *
 * All tab actions call back into the local embedded Spring Boot server on
 * http://localhost:9090, which routes to EdgeController.  Keeping the UI
 * out-of-process from the services via localhost HTTP means we never have
 * to worry about thread-affinity between Swing's EDT and Spring's
 * background pools — they talk JSON, nothing else.
 */
public class EdgeMainWindow extends JFrame {

    private static final String BASE = "http://localhost:9090";
    private final ObjectMapper json = new ObjectMapper();
    private final ConfigurableApplicationContext ctx;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField serialField   = new JTextField("EDGE-0001", 20);
    private final JTextField deviceField   = new JTextField("Edge Test Device", 20);
    private final JTextField modalityField = new JTextField("CT", 20);
    private final JTextField instituteField= new JTextField("HCLTech MedTech Lab", 20);
    private final JTextField manufacturerField = new JTextField("QuantixMed", 20);
    private final JTextField modelField    = new JTextField("EDGE-1", 20);
    private final JTextField aeTitleField  = new JTextField("EDGE01", 20);
    private final JTextArea  regLog = new JTextArea(6, 40);

    private final JLabel  connSerialLabel    = new JLabel("—");
    private final JLabel  connStatusLabel    = new JLabel("Not registered");
    private final JLabel  connHeartbeatLabel = new JLabel("0");
    private final JLabel  connLastHbLabel    = new JLabel("—");
    private final JLabel  connErrorLabel     = new JLabel("—");

    private final JTextField uploadPathField = new JTextField(30);
    private final JTextField uploadModality  = new JTextField("CT", 6);
    private final JTextArea  uploadLog       = new JTextArea(8, 50);

    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edge-ui-poller");
                t.setDaemon(true);
                return t;
            });

    public EdgeMainWindow(ConfigurableApplicationContext ctx) {
        super("QuantixMed Edge Connector");
        this.ctx = ctx;
        setSize(720, 560);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                poller.shutdownNow();
                ctx.close();
                System.exit(0);
            }
        });

        tabs.addTab("Register",  buildRegisterTab());
        tabs.addTab("Connected", buildConnectedTab());
        tabs.addTab("Upload",    buildUploadTab());
        tabs.setEnabledAt(1, false);
        tabs.setEnabledAt(2, false);

        setContentPane(tabs);

        poller.scheduleAtFixedRate(this::refreshStatus, 1, 2, TimeUnit.SECONDS);
    }

    // ── Register tab ──────────────────────────────────────────────────────
    private JPanel buildRegisterTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Serial Number *",   serialField);
        addRow(form, c, row++, "Device Name *",     deviceField);
        addRow(form, c, row++, "Modality *",        modalityField);
        addRow(form, c, row++, "Institute Name *",  instituteField);
        addRow(form, c, row++, "Manufacturer",      manufacturerField);
        addRow(form, c, row++, "Model",             modelField);
        addRow(form, c, row++, "AE Title",          aeTitleField);

        JButton submit = new JButton("Register Device");
        submit.addActionListener(e -> doRegister());
        c.gridx = 1; c.gridy = row;
        form.add(submit, c);

        regLog.setEditable(false);
        regLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(regLog);
        scroll.setBorder(BorderFactory.createTitledBorder("Log"));

        root.add(form, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        return root;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        form.add(field, c);
        c.weightx = 0;
    }

    private void doRegister() {
        appendLog(regLog, "Submitting registration...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                String body = json.writeValueAsString(java.util.Map.of(
                        "serialNumber",  serialField.getText().trim(),
                        "deviceName",    deviceField.getText().trim(),
                        "modality",      modalityField.getText().trim(),
                        "instituteName", instituteField.getText().trim(),
                        "manufacturer",  manufacturerField.getText().trim(),
                        "model",         modelField.getText().trim(),
                        "aeTitle",       aeTitleField.getText().trim()
                ));
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/edge/register").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) { out.write(body.getBytes()); }
                return new String(c.getInputStream().readAllBytes());
            }
            @Override protected void done() {
                try {
                    String resp = get();
                    JsonNode node = json.readTree(resp);
                    boolean ok = node.path("success").asBoolean(false);
                    appendLog(regLog, (ok ? "OK: " : "FAIL: ") + node.path("message").asText());
                    if (ok) {
                        tabs.setEnabledAt(1, true);
                        tabs.setEnabledAt(2, true);
                        tabs.setSelectedIndex(1);
                    }
                } catch (Exception e) {
                    appendLog(regLog, "ERROR: " + e.getMessage());
                }
            }
        }.execute();
    }

    // ── Connected tab ─────────────────────────────────────────────────────
    private JPanel buildConnectedTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel grid = new JPanel(new GridLayout(5, 2, 8, 10));
        grid.add(new JLabel("Serial:"));            grid.add(connSerialLabel);
        grid.add(new JLabel("MQTT status:"));       grid.add(connStatusLabel);
        grid.add(new JLabel("Heartbeat count:"));   grid.add(connHeartbeatLabel);
        grid.add(new JLabel("Last heartbeat:"));    grid.add(connLastHbLabel);
        grid.add(new JLabel("Last error:"));        grid.add(connErrorLabel);

        JTextArea info = new JTextArea(
                "\nThe Edge Connector publishes a heartbeat message every 10 seconds to:\n" +
                "    pacs/<serial>/status   (QoS 1)\n\n" +
                "It sets an MQTT Last Will on:\n" +
                "    pacs/<serial>/lwt      (delivered on unclean disconnect)\n\n" +
                "All connections use MQTT v3.1.1 over TLS 1.3 with client-cert auth.\n");
        info.setEditable(false);
        info.setBackground(new Color(245, 247, 255));

        root.add(grid, BorderLayout.NORTH);
        root.add(new JScrollPane(info), BorderLayout.CENTER);
        return root;
    }

    // ── Upload tab ────────────────────────────────────────────────────────
    private JPanel buildUploadTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; form.add(new JLabel("DICOM study ZIP:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        form.add(uploadPathField, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("ZIP files", "zip"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                uploadPathField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        form.add(browse, c);

        c.gridx = 0; c.gridy = 1; form.add(new JLabel("Modality:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(uploadModality, c);

        JButton up = new JButton("Upload");
        up.addActionListener(e -> doUpload());
        c.gridx = 1; c.gridy = 2; c.fill = GridBagConstraints.NONE;
        form.add(up, c);

        uploadLog.setEditable(false);
        uploadLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(uploadLog);
        scroll.setBorder(BorderFactory.createTitledBorder("Upload log"));

        root.add(form, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        return root;
    }

    private void doUpload() {
        String path = uploadPathField.getText().trim();
        if (path.isEmpty()) {
            appendLog(uploadLog, "Choose a ZIP file first.");
            return;
        }
        File f = new File(path);
        if (!f.exists()) {
            appendLog(uploadLog, "File does not exist: " + path);
            return;
        }
        appendLog(uploadLog, "Uploading " + f.getName() + " (" + f.length() + " bytes)...");

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                String boundary = "----ui" + System.currentTimeMillis();
                HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/edge/upload").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setChunkedStreamingMode(64 * 1024);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(("--" + boundary + "\r\n").getBytes());
                    out.write("Content-Disposition: form-data; name=\"modality\"\r\n\r\n".getBytes());
                    out.write((uploadModality.getText().trim() + "\r\n").getBytes());
                    out.write(("--" + boundary + "\r\n").getBytes());
                    out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + f.getName() + "\"\r\n").getBytes());
                    out.write("Content-Type: application/zip\r\n\r\n".getBytes());
                    out.write(Files.readAllBytes(f.toPath()));
                    out.write(("\r\n--" + boundary + "--\r\n").getBytes());
                }
                int code = c.getResponseCode();
                java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                return "HTTP " + code + ": " + (in != null ? new String(in.readAllBytes()) : "");
            }
            @Override protected void done() {
                try { appendLog(uploadLog, get()); }
                catch (Exception e) { appendLog(uploadLog, "ERROR: " + e.getMessage()); }
            }
        }.execute();
    }

    // ── Status poller ─────────────────────────────────────────────────────
    private void refreshStatus() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/edge/status").openConnection();
            c.setConnectTimeout(1000);
            c.setReadTimeout(1500);
            JsonNode node = json.readTree(c.getInputStream());
            boolean registered = node.path("registered").asBoolean(false);
            boolean mqttOk = node.path("mqttConnected").asBoolean(false);
            SwingUtilities.invokeLater(() -> {
                if (registered) {
                    tabs.setEnabledAt(1, true);
                    tabs.setEnabledAt(2, true);
                }
                connSerialLabel.setText(node.path("serialNumber").asText("—"));
                connStatusLabel.setText(mqttOk ? "● Connected" : "● Disconnected");
                connStatusLabel.setForeground(mqttOk ? new Color(22,163,74) : new Color(220,38,38));
                connHeartbeatLabel.setText(node.path("heartbeatCount").asText("0"));
                connLastHbLabel.setText(node.path("lastHeartbeatAt").asText("—"));
                connErrorLabel.setText(node.path("lastError").asText("—"));
            });
        } catch (Exception ignored) { /* service may still be starting */ }
    }

    private void appendLog(JTextArea area, String msg) {
        SwingUtilities.invokeLater(() -> {
            area.append(msg + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }
}
