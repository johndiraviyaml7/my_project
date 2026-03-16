# Edge PACS System — Three-Project Suite

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  EdgeConnectViewer (GUI App)                                         │
│  - Swing desktop UI                                                  │
│  - Browse & upload DICOM files → EdgeConnector REST API             │
│  - Publish MQTT connectivity ON/LWT OFF via WSS                     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ REST (HTTPS)
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│  EdgeConnector (Service)                                             │
│  - Watches DICOM folder, uploads to PACSConnector via HTTPS         │
│  - Publishes connectivity status via MQTT over WSS (client.crt)     │
│  - Publishes LWT topic on ungraceful disconnect                      │
│  - Exposes REST API for Viewer                                       │
└──────────────────────────┬──────────────────────────────────────────┘
          MQTT over WSS    │    HTTPS multipart upload
     (connectivity topics) │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│  PACSConnector (Server)                                              │
│  - Embedded Moquette MQTT Broker (SSL/WSS, requires client.crt)     │
│  - Exposes HTTPS upload API for DICOM files                          │
│  - Subscribes to connectivity + LWT topics from EdgeConnector        │
│  - Persists all events to SQLite DB                                  │
│  - Tables: pacs_device, pacs_connectivity_status, pacs_dicom_image  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

- **Java 17** (JDK, not JRE)
- **Maven 3.8+**
- **Eclipse IDE** with plugins:
  - Eclipse Maven (m2e) — bundled in Eclipse IDE for Java Developers
  - Spring Tools 4 (optional, for Spring Boot support)
- **Launch4j** (for .exe generation — auto-downloaded by Maven plugin)

---

## Certificate Setup (Required Before Build)

Place the following certificate files in each project's `src/main/resources/certs/` directory:

### For EdgeConnector & EdgeConnectViewer (client side):
| File | Description |
|------|-------------|
| `client.crt` | Client certificate (PEM) |
| `client.key` | Client private key (PEM) |
| `client.p12` | Client cert + key in PKCS12 format |
| `ca.crt` | CA certificate (PEM) |
| `truststore.jks` | Java TrustStore containing ca.crt |

### For PACSConnector (server side):
| File | Description |
|------|-------------|
| `server.crt` | Server certificate (PEM) |
| `server.key` | Server private key (PEM) |
| `server.p12` | Server cert + key in PKCS12 format |
| `ca.crt` | CA certificate (PEM) — used to verify client.crt |
| `truststore.jks` | Java TrustStore containing ca.crt |

### Generating Self-Signed Certificates for Testing

```bash
# 1. Generate CA key and certificate
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt \
  -subj "/CN=EdgePACS-CA/O=Edge Systems"

# 2. Generate server certificate (for PACSConnector)
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr \
  -subj "/CN=localhost/O=PACSConnector"
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key \
  -CAcreateserial -out server.crt
openssl pkcs12 -export -in server.crt -inkey server.key \
  -out server.p12 -name server -passout pass:changeit

# 3. Generate client certificate (for EdgeConnector)
openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr \
  -subj "/CN=EdgeConnector/O=Edge Systems"
openssl x509 -req -days 365 -in client.csr -CA ca.crt -CAkey ca.key \
  -CAcreateserial -out client.crt
openssl pkcs12 -export -in client.crt -inkey client.key \
  -out client.p12 -name client -passout pass:changeit

# 4. Create TrustStore (containing CA cert)
keytool -import -trustcacerts -alias ca-root \
  -file ca.crt -keystore truststore.jks -storepass changeit -noprompt
```

---

## Importing into Eclipse

1. Open Eclipse → **File → Import → Existing Maven Projects**
2. Browse to this folder, check all three projects, click **Finish**
3. Eclipse will auto-download Maven dependencies (may take a few minutes)
4. Right-click each project → **Maven → Update Project** (Alt+F5)

---

## Configuration

Edit `src/main/resources/application.properties` in each project:

### EdgeConnector
```properties
mqtt.broker.url=wss://YOUR-PACS-HOST:9001/mqtt
pacs.upload.url=https://YOUR-PACS-HOST:8443/api/dicom/upload
dicom.watch.folder=C:/dicom/inbox
```

### EdgeConnectViewer
```properties
mqtt.broker.url=wss://YOUR-PACS-HOST:9001/mqtt
edge.connector.url=http://localhost:8080
```

### PACSConnector
```properties
# No changes needed if running on same host
# Adjust ports if needed
```

---

## Building

### Build All Projects (produces JAR + EXE)

```bash
# EdgeConnector
cd EdgeConnector
mvn clean package -DskipTests
# Output: target/EdgeConnector-1.0.0.jar
#         target/EdgeConnector.exe

# EdgeConnectViewer
cd ../EdgeConnectViewer
mvn clean package -DskipTests
# Output: target/EdgeConnectViewer-1.0.0.jar
#         target/EdgeConnectViewer.exe

# PACSConnector
cd ../PACSConnector
mvn clean package -DskipTests
# Output: target/PACSConnector-1.0.0.jar
#         target/PACSConnector.exe
```

### Build from Eclipse
Right-click project → **Run As → Maven build...** → Goals: `clean package`

---

## Running

### Start Order (important!)
1. **PACSConnector** first — starts the embedded MQTT broker
2. **EdgeConnector** — connects to PACSConnector broker
3. **EdgeConnectViewer** — connects to EdgeConnector API + broker

```bash
# Terminal 1 - PACSConnector
java -jar PACSConnector/target/PACSConnector-1.0.0.jar
# Or: PACSConnector/target/PACSConnector.exe

# Terminal 2 - EdgeConnector
java -jar EdgeConnector/target/EdgeConnector-1.0.0.jar
# Or: EdgeConnector/target/EdgeConnector.exe

# Double-click or run:
# EdgeConnectViewer/target/EdgeConnectViewer.exe
```

---

## SQLite Database (PACSConnector)

The database file `pacs-connector.db` is auto-created in the working directory.

### Schema (auto-created by Hibernate)

**pacs_device** — registered Edge devices
- `id`, `device_name`, `device_type`, `ip_address`, `mqtt_client_id`, `description`, `registered_at`, `updated_at`, `active`

**pacs_connectivity_status** — MQTT connectivity events (both normal + LWT)
- `id`, `pacs_device_id` (FK), `status` (connected/disconnected), `message_source` (mqtt_status/lwt), `disconnect_reason`, `mqtt_topic`, `raw_payload`, `received_at`

**pacs_dicom_image** — uploaded DICOM files
- `id`, `pacs_device_id` (FK), `original_filename`, `stored_path`, `file_size_bytes`, `patient_id`, `study_instance_uid`, `series_instance_uid`, `sop_instance_uid`, `modality`, `study_date`, `status`, `error_message`, `received_at`, `processed_at`

---

## REST API Reference

### EdgeConnector (port 8080)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/status` | Service health + MQTT connection status |
| POST | `/api/dicom/upload` | Upload DICOM file (multipart) |
| POST | `/api/mqtt/publish?topic=X` | Publish MQTT message |
| POST | `/api/mqtt/subscribe?topic=X` | Subscribe to MQTT topic |
| POST | `/api/connectivity/update?status=X` | Publish connectivity status |

### PACSConnector (port 8443, HTTPS)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/status` | Service health |
| POST | `/api/dicom/upload` | Receive DICOM from EdgeConnector |
| GET | `/api/devices` | List all PACS devices |
| POST | `/api/devices` | Register PACS device |
| GET | `/api/devices/{id}/connectivity` | Connectivity history for device |
| GET | `/api/connectivity/lwt` | All LWT (ungraceful disconnect) events |
| GET | `/api/images` | All stored DICOM images |
| GET | `/api/devices/{id}/images` | Images by device |
| GET | `/api/images/patient/{patientId}` | Images by patient |

---

## MQTT Topics

| Topic | Publisher | Subscriber | Description |
|-------|-----------|------------|-------------|
| `edge/connectivity/status` | EdgeConnector, EdgeViewer | PACSConnector | Normal connectivity status |
| `edge/connectivity/lwt` | MQTT Broker (automatic) | PACSConnector | Last Will — ungraceful disconnect |

---

## Deployment Notes for Windows EXE

The `.exe` files created by Launch4j require Java 17+ to be installed on the target machine (or bundled in a `jre/` folder next to the `.exe`).

To bundle JRE alongside EXE:
1. Copy JRE 17 folder to `jre/` next to your `.exe`
2. Launch4j will use the bundled JRE automatically

---

## Project Structure

```
EdgeConnector/
├── .classpath              ← Eclipse classpath
├── .project                ← Eclipse project metadata
├── .settings/              ← Eclipse compiler settings
├── pom.xml                 ← Maven build (JAR + EXE)
└── src/
    └── main/
        ├── java/com/edge/connector/
        │   ├── EdgeConnectorApplication.java
        │   ├── config/   (MqttConfig, WebSocketConfig, HttpClientConfig)
        │   ├── mqtt/     (MqttService)
        │   ├── service/  (DicomUploadService)
        │   └── controller/ (EdgeConnectorController)
        └── resources/
            ├── application.properties
            └── certs/    ← Place client.p12, truststore.jks here

EdgeConnectViewer/
├── .classpath / .project / .settings/
├── pom.xml
└── src/main/java/com/edge/viewer/
    ├── EdgeConnectViewerApplication.java
    ├── config/   (ViewerConfig)
    ├── mqtt/     (ViewerMqttService)
    ├── service/  (ViewerDicomService)
    └── ui/       (MainWindow)

PACSConnector/
├── .classpath / .project / .settings/
├── pom.xml
└── src/main/java/com/pacs/connector/
    ├── PACSConnectorApplication.java
    ├── config/   (MqttBrokerConfig, WebSocketConfig, AppConfig)
    ├── mqtt/     (PacsClientMqttService)
    ├── model/    (PacsDevice, PacsConnectivityStatus, PacsDicomImage)
    ├── repository/ (3 JPA repositories)
    ├── service/  (ConnectivityStatusService, DicomStorageService)
    └── controller/ (PACSConnectorController)
```
