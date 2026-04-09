# QuantixMed — Orthanc + OHIF Setup (v4)

## What changed from v3

The container was starting successfully but our **custom health check was failing**,
causing Docker to mark Orthanc as "unhealthy" and block OHIF from starting.

**Root cause:** Newer `orthancteam/orthanc` images are based on Ubuntu 25.10 and
the built-in probe script (`/probes/test-aliveness.py`) is used instead of `curl`.
When we overrode the health check with our own `curl` command, it conflicted.

**Fix applied:**
- Set `healthcheck: disable: true` — let the image manage its own health probe
- Removed `condition: service_healthy` from OHIF — both containers start
  independently. OHIF only needs Orthanc when a user opens a study in the browser,
  not at container startup time.
- `start.bat` now waits 15 seconds then tests Orthanc with a direct curl call
  instead of relying on Docker's health status

---

## Quick Start

1. Make sure **Docker Desktop is running** (whale in system tray)
2. Double-click **`start.bat`**

---

## Service URLs

| Service | URL | Login |
|---------|-----|-------|
| Orthanc Explorer 2 | http://localhost:8042/ui/app | admin / admin |
| Orthanc REST API | http://localhost:8042 | admin / admin |
| DICOMweb root | http://localhost:8042/dicom-web | admin / admin |
| OHIF Viewer | http://localhost:3000 | — |

---

## Complete startup order

Run each in a **separate Command Prompt window**:

```
Window 1 (this folder):    start.bat
Window 2 (springboot_backend): mvn spring-boot:run
Window 3 (reactjs_frontend):   npm run dev
```

Then to load DICOM data:
```
Window 4 (dicom_parser):   python run_pipeline.py --psma-dir ..\psma_data
```

---

## Troubleshooting

### Orthanc container exits immediately
Run `logs.bat` and look for the error. Common causes:
- Port 8042 is already in use → change `"8042:8042"` to `"8043:8042"` in docker-compose.yml
- Volume permission issue → run `docker volume rm orthanc_config_v4_orthanc_db` to reset

### OHIF shows blank page or "No studies"
- Orthanc has no data yet → run the Python parser first
- Spring Boot is not running → start it on port 8080
- Check OHIF can reach Spring Boot: open http://localhost:8080/orthanc/status

### Port conflicts
Change the host port (left number) in docker-compose.yml:
```yaml
ports:
  - "8043:8042"   # Orthanc on host port 8043
  - "3001:80"     # OHIF on host port 3001
```
Then update `application.properties`: `app.orthanc.url=http://localhost:8043`
And `ohif-app-config.js`: change `8080` to your Spring Boot port.
