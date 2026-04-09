# QuantixMed Edge Connector

Windows-side client that registers with the PAS Connector, publishes
MQTT heartbeats, and uploads DICOM study zips.

## Layout

```
edge_connector/
├── src/main/java/com/quantixmed/edge/
│   ├── EdgeConnectorApplication.java    # main, launches Spring Boot + Swing
│   ├── controller/EdgeController.java   # /edge/register /edge/status /edge/upload
│   ├── dto/EdgeDtos.java
│   ├── mqtt/EdgeMqttPublisher.java      # Paho MQTT v3.1.1 over TLS 1.3
│   ├── service/
│   │   ├── KeystoreInitializer.java     # PEM → PKCS12/JKS at first boot
│   │   ├── PasClient.java               # mTLS HTTPS client to PAS Connector
│   │   └── TlsContextFactory.java       # shared SSLContext (TLSv1.3)
│   └── ui/EdgeMainWindow.java           # Swing tabs: Register / Connected / Upload
├── src/main/resources/application.properties
├── certs/                               # PEM files — ca.crt client.crt client.key
├── pom.xml
└── run.bat
```

## Build the jar

```powershell
cd edge_connector
mvn -DskipTests package
```

Output: `target/edge-connector.jar` (fat Spring Boot jar, ~25 MB).

## Run on a dev workstation (JRE already installed)

```powershell
# Copy these to one folder:
#   edge-connector.jar
#   application.properties  (optional — the jar contains a default copy)
#   certs/  (ca.crt client.crt client.key)
#   run.bat

run.bat
```

First boot materialises `certs/client.p12` and `certs/truststore.jks`
from the PEM files with password `changeit`. The Spring Boot REST
service starts on `http://localhost:9090`, then the Swing window opens.

## Turn it into a Windows .exe

Two options, in order of recommended effort:

### Option 1 — jpackage (JDK 17+, produces a proper installer)

```powershell
jpackage ^
  --type app-image ^
  --name QuantixMedEdge ^
  --input target ^
  --main-jar edge-connector.jar ^
  --main-class com.quantixmed.edge.EdgeConnectorApplication ^
  --icon assets\quantixmed.ico ^
  --win-console ^
  --java-options "--ui"
```

Output: `QuantixMedEdge/QuantixMedEdge.exe` plus a private JRE — no
Java installation needed on the target machine. Copy the whole
folder alongside `certs/` and double-click the exe.

### Option 2 — Launch4j (wraps the fat jar, needs JRE on target)

Download Launch4j, run the GUI, point it at `target/edge-connector.jar`,
set main class to `com.quantixmed.edge.EdgeConnectorApplication`, add
program argument `--ui`, and save as `edge-connector.exe`. Ship the
exe plus the `certs/` folder plus a README telling the user to install
Temurin JRE 21 first.

Launch4j is the fastest path to "it runs on Windows as .exe"; jpackage
is better for distributions where you don't want users installing Java.

## Environment overrides

Edit `application.properties` or pass `-D` flags:

```powershell
java -Dedge.pas.base-url=https://pas.internal:8443 ^
     -Dedge.pas.mqtt-url=ssl://pas.internal:8883 ^
     -jar edge-connector.jar --ui
```

## Security notes

- The `changeit` keystore password is for **development only**. In
  production, re-issue certs with a strong password, update the
  PEM-to-keystore step, and keep the PEM private keys off the client
  machine entirely (use a Windows key store instead).
- `setHttpsHostnameVerificationEnabled(false)` in `EdgeMqttPublisher`
  is necessary for the self-signed dev certs where the SAN doesn't
  match `localhost`. Remove this for production CAs.
- The local REST server on :9090 is plain HTTP because only the Swing
  UI running on the same machine talks to it. If you ever need to
  expose it to the network, add a firewall rule and enable TLS there
  too.
