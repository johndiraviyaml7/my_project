# CHANGELOG — v5

This release adds two new projects and wires them into the existing
QuantixMed v4 platform:

- **PAS Connector** — Spring Boot server running in Docker, exposes
  mTLS REST for Edge device registration and DICOM zip upload, plus
  an embedded MQTT broker for device connectivity / LWT / upload-status
  topics.
- **Edge Connector + Swing Viewer** — single Spring Boot jar that runs
  on a Windows client machine, registers itself with PAS, publishes
  MQTT heartbeats over TLS 1.3, and uploads DICOM study zips. Ships
  with a Swing UI (Register / Connected / Upload tabs) as an alternate
  entry point in the same jar.

The existing v4 services (postgres, orthanc, dicom_parser_api, parser_init,
springboot_backend, ohif, reactjs_frontend) are unchanged except for
the React dashboard, which now polls PAS Connector for live MQTT status.

---

## Architecture

```
                    ┌──────────────────────────────────────────────┐
                    │                Windows client                │
                    │  ┌──────────┐      ┌────────────────────┐    │
                    │  │  Swing   │◀────▶│  Edge Connector    │    │
                    │  │  Viewer  │ HTTP │  (Spring Boot jar) │    │
                    │  │          │      │  localhost:9090    │    │
                    │  └──────────┘      └────────┬───────────┘    │
                    └──────────────────────────────┼───────────────┘
                                                   │ mTLS
                                    ┌──────────────┼────────────┐
                                    │              │            │
                           HTTPS mTLS          MQTT v3 + TLS
                           :8443                :8883
                                    │              │
                                    ▼              ▼
                    ┌──────────────────────────────────────────┐
                    │         PAS Connector (Docker)           │
                    │   Spring Boot @ :8443  (mTLS REST)       │
                    │     POST /api/pas/register               │
                    │     POST /api/pas/upload/{serial}        │
                    │                                          │
                    │   Plain HTTP @ :8444 (dashboard poll)    │
                    │     GET  /api/pas/devices/status         │
                    │     GET  /api/pas/events/recent          │
                    │     GET  /api/pas/health                 │
                    │                                          │
                    │   Embedded Moquette broker               │
                    │     plain :1883  (self-subscriber only)  │
                    │     TLS   :8883  (external edges)        │
                    │     topics: pacs/{serial}/{status|lwt|   │
                    │                            upload-status}│
                    └──────────┬───────────────────────────────┘
                               │ JDBC
                               ▼
                    ┌──────────────────────┐
                    │  Postgres (existing) │
                    │   pacs_devices       │
                    │   + last_seen_at     │  ← new column
                    │   pacs_status_events │  ← new table
                    └──────────────────────┘
                               ▲
                               │  poll every 5s
                    ┌──────────┴───────────┐
                    │  React Dashboard     │
                    │  live "● live · Ns   │
                    │  ago" indicator      │
                    └──────────────────────┘
```

## Ports

| Port  | Service             | Protocol  | Purpose                                |
|-------|---------------------|-----------|----------------------------------------|
| 5432  | Postgres            | TCP       | (unchanged)                            |
| 5173  | React frontend      | HTTP      | (unchanged)                            |
| 8000  | dicom_parser_api    | HTTP      | (unchanged)                            |
| 8042  | Orthanc             | HTTP      | (unchanged)                            |
| 8080  | Spring Boot backend | HTTP      | (unchanged)                            |
| 8443  | **PAS Connector**   | HTTPS+mTLS| register + upload (client cert req'd)  |
| 8444  | **PAS Connector**   | HTTP      | dashboard status polling (no cert)     |
| 8883  | **PAS MQTT broker** | MQTT/TLS  | edge heartbeat + LWT + upload-status   |
| 1883  | **PAS MQTT broker** | MQTT      | localhost self-subscriber only         |
| 9090  | Edge Connector      | HTTP      | Swing UI → local Spring Boot           |

## MQTT topic structure

All topics follow `pacs/<serial>/<event>`:

- **`pacs/<serial>/status`** — JSON heartbeat published by Edge Connector
  every 10s. Payload: `{serial, seq, ts}`. QoS 1.
- **`pacs/<serial>/lwt`** — MQTT Last Will & Testament. Broker delivers
  this on unclean disconnect. Payload: `{serial, reason, ts}`. QoS 1.
- **`pacs/<serial>/upload-status`** — published by Edge Connector as it
  starts/completes/fails a DICOM zip upload. Payload:
  `{serial, phase, detail, ts}` where `phase ∈ {STARTED, COMPLETED, FAILED}`.

PAS Connector's in-JVM self-subscriber routes each message to:

1. `DeviceService.markHeartbeat(serial)` / `markDisconnected(serial)` —
   updates `pacs_devices.status` and `last_seen_at`.
2. `StatusEventService.record(...)` — appends to `pacs_status_events`.

A scheduled sweeper (`StaleDeviceSweeper`, every 10s) flips any
device still labelled "Connected" whose `last_seen_at` is older than
the configured heartbeat timeout (default 30s) back to "Disconnected".

## Certificate handling

You supplied `ca.crt`, `server.crt`, `server.key`, `client.crt`,
`client.key` (PEM format, no passwords on the private keys).

**PAS Connector** Dockerfile converts these at *build* time into:
- `server.p12` — PKCS12 keystore, alias `medserver`, password `changeit`
- `truststore.jks` — JKS truststore with the root CA, password `changeit`

**Edge Connector** materialises the same way at *first boot* via the
`KeystoreInitializer` bean: reads the PEMs from the adjacent `certs/`
folder, writes `client.p12` and `truststore.jks` with password `changeit`.

> The `changeit` password is for development only. Before any production
> deployment, re-issue certificates with a strong password, change the
> values in `application.properties` for both projects, and rebuild.

## Registration flow

```
Edge Connector                      PAS Connector
──────────────                      ─────────────
    │
    │  POST /edge/register (local HTTP on :9090)
    │      body: {serialNumber, deviceName, modality, instituteName,...}
    │
    │  ─── SSL handshake (client.p12) ────────▶
    │  POST https://pas:8443/api/pas/register
    │      (mTLS verifies client cert is signed by ca.crt)
    │                                          │
    │                                          │ upsert pacs_devices by serial
    │                                          │ log CN from X509Certificate
    │                                          │
    │  ◀──── 200 OK {id, serialNumber, ...}────│
    │
    │  EdgeMqttPublisher.connectAs(serial)
    │      ── TLSv1.3 handshake ──▶
    │  MQTT CONNECT to ssl://pas:8883
    │  SUBSCRIBE none (publisher only)
    │  WILL: pacs/<serial>/lwt
    │
    │  PUBLISH pacs/<serial>/status every 10s
    │                                          │
    │                                          │ self-subscriber receives,
    │                                          │ updates status = Connected,
    │                                          │ sets last_seen_at = now()
    ▼                                          ▼
```

React dashboard polls `GET /api/pas/devices/status` every 5s; any
device whose `last_seen_at` is within the 30s window shows as
**● live · Ns ago** with a green badge.

## Files added

### pas_connector/
- `pom.xml`
- `Dockerfile`
- `src/main/resources/application.properties`
- `src/main/resources/schema-v5-delta.sql`
- `src/main/java/com/quantixmed/pas/PasConnectorApplication.java`
- `src/main/java/com/quantixmed/pas/config/WebServerConfig.java`
- `src/main/java/com/quantixmed/pas/config/PortGatingFilter.java`
- `src/main/java/com/quantixmed/pas/controller/RegistrationController.java`
- `src/main/java/com/quantixmed/pas/controller/UploadController.java`
- `src/main/java/com/quantixmed/pas/controller/StatusController.java`
- `src/main/java/com/quantixmed/pas/dto/PasDtos.java`
- `src/main/java/com/quantixmed/pas/model/PacsDevice.java`
- `src/main/java/com/quantixmed/pas/model/StatusEvent.java`
- `src/main/java/com/quantixmed/pas/mqtt/MqttBrokerBean.java`
- `src/main/java/com/quantixmed/pas/mqtt/MqttSelfSubscriber.java`
- `src/main/java/com/quantixmed/pas/repository/PacsDeviceRepository.java`
- `src/main/java/com/quantixmed/pas/repository/StatusEventRepository.java`
- `src/main/java/com/quantixmed/pas/service/DeviceService.java`
- `src/main/java/com/quantixmed/pas/service/StaleDeviceSweeper.java`
- `src/main/java/com/quantixmed/pas/service/StatusEventService.java`
- `certs/{ca.crt, ca.key, server.crt, server.key, client.crt, client.key}`

### edge_connector/
- `pom.xml`
- `run.bat`
- `README.md`
- `src/main/resources/application.properties`
- `src/main/java/com/quantixmed/edge/EdgeConnectorApplication.java`
- `src/main/java/com/quantixmed/edge/controller/EdgeController.java`
- `src/main/java/com/quantixmed/edge/dto/EdgeDtos.java`
- `src/main/java/com/quantixmed/edge/mqtt/EdgeMqttPublisher.java`
- `src/main/java/com/quantixmed/edge/service/KeystoreInitializer.java`
- `src/main/java/com/quantixmed/edge/service/TlsContextFactory.java`
- `src/main/java/com/quantixmed/edge/service/PasClient.java`
- `src/main/java/com/quantixmed/edge/ui/EdgeMainWindow.java`
- `certs/{ca.crt, client.crt, client.key}`

### Modified existing files

- `docker-compose.yml` — added `pas_connector` service + `pas_uploads` volume
- `dicom_parser/models/schema.sql` — appended v5 delta (last_seen_at, pacs_status_events)
- `reactjs_frontend/src/api/pasService.js` — new, polls PAS Connector
- `reactjs_frontend/src/pages/DicomDashboard.jsx` — merges live PAS status into the table
- `reactjs_frontend/vite.config.js` — added `VITE_PAS_URL` default

## Running it

### Stack (PAS Connector + everything else)

```powershell
docker compose down -v         # wipe old DB; schema delta needs fresh start
docker compose up -d --build
docker compose logs -f pas_connector
```

Wait for the PAS Connector logs to show:
```
Moquette broker started: plain=0.0.0.0:1883, tls=0.0.0.0:8443, ...
Self-subscriber connected to tcp://localhost:1883 and subscribed to pacs/+/...
Tomcat started on port(s): 8443 (https), 8444 (http)
```

Quick verification from PowerShell:
```powershell
curl http://localhost:8444/api/pas/health
curl http://localhost:8444/api/pas/devices/status
```

### Edge Connector (on a Windows client)

```powershell
cd edge_connector
mvn -DskipTests package
cd target
copy ..\certs .\certs /E /Y     # or xcopy
java -jar edge-connector.jar --ui
```

The Swing window opens. Fill the Register tab (pre-populated with test
data), click **Register Device**. On success:
1. Connected and Upload tabs become enabled
2. The Connected tab starts showing **● Connected** and a rising
   heartbeat counter
3. In the React dashboard (http://localhost:5173), the device row will
   show **● live · Ns ago** within ~5 seconds

On the Upload tab, choose a DICOM zip and click Upload. The upload:
1. publishes `pacs/<serial>/upload-status` with `phase=STARTED`
2. POSTs multipart to `https://pas:8443/api/pas/upload/<serial>`
3. publishes `phase=COMPLETED` or `FAILED`
4. the file lands in the `pas_uploads` volume: `/app/uploads/<serial>/<timestamp>_<name>.zip`

## Packaging the Edge Connector as .exe

See `edge_connector/README.md` for two options:
1. **jpackage** (JDK 17+) — produces a full app-image with bundled JRE,
   no Java needed on target machine
2. **Launch4j** — wraps the fat jar, smaller, but target machine needs
   a JRE installed

## Known limitations & what to test

1. **First build takes 10–15 minutes.** PAS Connector pulls Moquette +
   Paho + Spring Boot 3.2.5 via Maven. Subsequent builds are seconds.

2. **I could not run Maven in the sandbox**, so the Spring Boot jars
   for `pas_connector` and `edge_connector` have never been compiled.
   The first `docker compose up --build pas_connector` will surface
   any classpath/dependency issues. Most likely risks:
   - Moquette 0.17 + Netty version skew with Spring Boot 3.2.5's Netty
   - SLF4J binding collision between Moquette and Logback (I excluded
     `slf4j-log4j12` in the pom but there may be others)
   - Moquette `IConfig` property name changes between versions
   If the PAS container fails to start, `docker compose logs pas_connector`
   and share the stack trace — most of these are 1-line fixes.

3. **The self-signed certs have no SANs** (we verified this on the
   server.crt during Pass 5 investigation). The Edge Connector disables
   hostname verification for MQTT. This is fine for dev — **do not use
   in production**.

4. **The parser re-runs the full schema** on startup, including the new
   v5 delta. That means running `parser_init` against a v4 database
   that was already populated will also create the new tables and
   column — the `IF NOT EXISTS` guards make it safe.

5. **Registration is currently upsert-only.** If a device re-registers
   with the same serial number, we update the existing row rather than
   reject the request. This is the right behaviour for dev; production
   probably wants a "re-register requires admin approval" flow.

6. **The bootstrap-vs-client cert distinction in your original spec**
   was collapsed into a single cert for this pass. If you need a true
   two-stage flow (Edge ships with a bootstrap cert, calls /register,
   PAS issues a new client cert signed by the root CA), that's a
   separate PKI project on top of v5.

## Expected first-run issues

When you run `docker compose up -d --build pas_connector` for the first
time, at least one of these is likely:

1. **Moquette classpath issue.** If `pas_connector` crashes at startup
   with a `NoSuchMethodError` or `ClassNotFoundException` mentioning
   io.netty or io.moquette, we may need to pin a specific Netty version
   in the pom. Share the stack trace.

2. **Moquette `IConfig` property name.** Moquette has renamed properties
   between versions. If startup logs show "unrecognised property", tell
   me which ones and I'll update `MqttBrokerBean.java`.

3. **Database schema conflict.** If the v4 backend was already running
   with tables in place, the `ALTER TABLE` / `CREATE TABLE IF NOT EXISTS`
   should be idempotent. If it isn't (e.g. a column type mismatch),
   wiping with `docker compose down -v` will solve it.

4. **The `_subject_count_per_device` sum query** in `PacsDeviceService`
   from the main backend might start failing if the new device rows
   have `null` subject counts. The existing null-safety code handles it,
   but worth watching.

For each of the above, the fix is typically 1–5 lines once I can see the
actual error message.
