# QuantixMed DICOM Platform

A four-service DICOM ingestion, de-identification, storage, and viewing
platform, packaged for **Docker Desktop on Windows (WSL2 backend)**.

```
                      Docker Desktop network: quantixmed_net
  ┌────────────────────────────────────────────────────────────────────────┐
  │                                                                        │
  │   postgres:5432    orthanc:8042 / 4242     ohif (nginx):80             │
  │       ▲                  ▲                       ▲                     │
  │       │                  │                       │                     │
  │       ├──────┬───────────┤                       │                     │
  │       │      │           │                       │ iframe              │
  │  dicom_parser_api   springboot_backend ──────────┘ /viewer?...         │
  │       :8000              :8080                                         │
  │                            ▲                                           │
  │                            │  REST + /orthanc/* proxy                  │
  │                            │                                           │
  │                       reactjs_frontend (nginx):80                      │
  │                                                                        │
  └────────────────────────────────────────────────────────────────────────┘

  Browser (Windows host) reaches:
    http://localhost:5173   React UI
    http://localhost:8080   Spring Boot REST + /orthanc/* DICOMweb proxy
    http://localhost:3000   OHIF Viewer
    http://localhost:8042   Orthanc (admin / admin)
    http://localhost:5432   PostgreSQL (postgres / postgres)
```

---

## Services

| Service              | Image / Build                | Host port | Purpose                                       |
|----------------------|------------------------------|-----------|-----------------------------------------------|
| `postgres`           | `postgres:16-alpine`         | 5432      | Subjects / studies / series / instances       |
| `orthanc`            | `orthancteam/orthanc:latest` | 8042 / 4242 | DICOM server + DICOMweb (WADO-RS / QIDO-RS) |
| `dicom_parser_api`   | `./dicom_parser`             | 8000      | FastAPI for ad-hoc DICOM uploads              |
| `parser_init`        | `./dicom_parser` (one-shot)  | —         | Walks `PSMA_one_dicom`, de-identifies, loads DB, uploads to Orthanc |
| `springboot_backend` | `./springboot_backend`       | 8080      | REST API + Orthanc reverse proxy (`/orthanc/**`) |
| `ohif`               | `ohif/app:v3.9.0`            | 3000      | OHIF Viewer v3                                |
| `reactjs_frontend`   | `./reactjs_frontend`         | 5173      | React UI (nginx)                              |

---

## Prerequisites

1. **Docker Desktop for Windows** with the WSL2 backend enabled.
2. **At least 8 GB RAM** allocated to Docker Desktop (Settings → Resources).
3. **File sharing**: the project folder must live on a drive that Docker
   Desktop can access. Easiest path: clone or unzip somewhere under
   `C:\Users\<you>\` so it's auto-shared with WSL2.

---

## Getting started

### 1. Place your DICOM data

Drop your DICOM tree into `PSMA_one_dicom/` at the project root. The parser
expects this exact hierarchy:

```
PSMA_one_dicom/
  <subject_id>/
    <study_instance_uid>/
      <series_instance_uid>/
        1-001.dcm
        1-002.dcm
        ...
```

The sample `PSMA_0b1200b317289bbb` folder from the original
`New_Dicom_Tool_Backend.zip` can be dropped in as-is.

### 2. Start the long-running services

From the project root (where `docker-compose.yml` lives), open
**PowerShell** or **Windows Terminal** and run:

```powershell
docker compose up -d --build
```

First build takes 5–10 minutes (Maven download + Vite build + image pulls).
Subsequent starts are seconds.

Watch the logs until everything is healthy:

```powershell
docker compose ps
docker compose logs -f springboot_backend
```

### 3. Run the parser pipeline (one-shot)

This walks `PSMA_one_dicom/`, de-identifies every DICOM file, populates
PostgreSQL, and uploads the de-identified files to Orthanc:

```powershell
docker compose --profile init run --rm parser_init
```

You'll see output like:

```
Subject PSMA_0b1200b317289bbb done: 1 studies / 3 series / 649 instances
Pipeline complete — 1 studies | 3 series | 649 instances
```

### 4. Verify each layer

| Check                              | Command / URL                                                     |
|------------------------------------|-------------------------------------------------------------------|
| Postgres rows                      | `docker compose exec postgres psql -U postgres -d quantixmed_dicom -c "SELECT subject_id, total_studies, total_series, total_instances FROM subjects;"` |
| Orthanc study count                | http://localhost:8042/studies (login `admin` / `admin`)           |
| Orthanc study list (JSON)          | http://localhost:8042/app/explorer.html                           |
| Spring Boot health                 | http://localhost:8080/api/health                                  |
| Spring Boot Orthanc proxy          | http://localhost:8080/orthanc/dicom-web/studies                   |
| OHIF Viewer                        | http://localhost:3000                                             |
| React UI                           | http://localhost:5173                                             |

If `/orthanc/dicom-web/studies` returns a JSON list of studies,
**the viewer pipeline is wired correctly** and OHIF will render images.

---

## How the de-identification works

The parser implements **DICOM PS3.15 Annex E — Basic Application
Confidentiality Profile** plus a Retain Longitudinal Temporal option (date
offset). For every DICOM file under `PSMA_one_dicom/<subject>/<study>/<series>/`:

| Action | Tags                              | What happens                                |
|--------|-----------------------------------|---------------------------------------------|
| **Z**  | PatientName, PatientID, AccessionNumber, ReferringPhysicianName | Replaced with anonymous / surrogate values |
| **X**  | PatientBirthDate, PatientAddress, PatientTelephoneNumbers, InstitutionName, etc. | Removed entirely |
| **U**  | StudyInstanceUID, SeriesInstanceUID, SOPInstanceUID, FrameOfReferenceUID | Replaced with deterministic surrogate `2.25.*` UIDs |
| **D**  | StudyDate, SeriesDate, AcquisitionDate, ContentDate | Offset by −1000 days (preserves temporal relationships) |
| **K**  | Modality, BodyPartExamined, PixelSpacing, etc. | Kept unchanged (clinically necessary) |

Plus all private tags are removed, `PatientIdentityRemoved=YES` and
`DeidentificationMethod` are injected, and a deterministic
`ANON-<sha256[:16]>` surrogate ID is generated per subject.

For each instance the parser writes **two file paths** to the `instances`
table:

- `file_path` — original DICOM on the bind-mounted host folder
- `deid_file_path` — de-identified DICOM under `/app/output/deid_dicom/<surrogate>/...` (in the `deid_output` named volume)

Both raw and de-identified files are preserved on disk. Only the
de-identified copy is uploaded to Orthanc.

---

## Why the viewer was previously not rendering images

The original `orthanc_config_v4/docker-compose.yml` only started Orthanc
and OHIF. The OHIF config and the React `orthancService.js` both expect
their DICOMweb calls to be routed through
`http://localhost:8080/orthanc/dicom-web` — which is the Spring Boot
reverse proxy. Because Spring Boot was not running in that compose,
those calls hit a closed port and OHIF showed nothing.

This unified `docker-compose.yml` runs Spring Boot in the same stack, so
the proxy is live and OHIF / the React app can reach Orthanc through it.
**No code changes to the viewer pipeline were required.**

---

## Common operations

```powershell
# View logs for everything
docker compose logs -f

# View logs for one service
docker compose logs -f dicom_parser_api

# Restart one service after a code change
docker compose up -d --build springboot_backend

# Re-run the parser pipeline (e.g. after dropping new DICOMs in PSMA_one_dicom)
docker compose --profile init run --rm parser_init

# Open a psql shell
docker compose exec postgres psql -U postgres -d quantixmed_dicom

# Stop everything
docker compose down

# Stop and wipe all data (Postgres + Orthanc + de-identified files)
docker compose down -v
```

---

## Troubleshooting

**`docker compose up` fails on volume mount of `./PSMA_one_dicom`**
The folder must exist before `up`. Even if it's empty (or just contains
`README.txt`), Docker Desktop on Windows creates the bind mount fine.

**Spring Boot can't connect to Postgres on startup**
The compose has a `depends_on: postgres: condition: service_healthy` so
this should not happen. If it does, check `docker compose logs postgres`
for the healthcheck output.

**OHIF loads but shows "No studies"**
1. Run the parser: `docker compose --profile init run --rm parser_init`
2. Verify Orthanc has studies: http://localhost:8042/app/explorer.html
3. Verify the proxy works: `curl http://localhost:8080/orthanc/dicom-web/studies`

**Images load in OHIF but the carousel on the Subject page is empty**
The carousel calls the parser FastAPI on port 8000. Confirm it's running:
`curl http://localhost:8000/api/health`. If it isn't, check
`docker compose logs dicom_parser_api`. The most common cause is the
parser pipeline not having been run yet — the carousel needs `filePath`
and `deidFilePath` populated in the `instances` table. Run:
`docker compose --profile init run --rm parser_init`.

**Maven build fails inside the springboot_backend container with proxy errors**
Your Docker Desktop is behind a corporate proxy. Configure it under
Settings → Resources → Proxies, then `docker compose build --no-cache springboot_backend`.

**Vite build is very slow or hangs**
Allocate at least 4 GB RAM to Docker Desktop and ensure the project is
on an SSD path that WSL2 can access without going through `\\wsl$`.

---

## What this delivers

- A running baseline of all four services with the viewer pipeline fixed
- DICOM parser walks `subject → study → series → instance`,
  de-identifies (DICOM PS3.15 Annex E + HIPAA Safe Harbor), persists
  both raw and de-identified file paths to Postgres
- Orthanc receives only de-identified DICOMs; OHIF reads via DICOMweb
  through the Spring Boot reverse proxy (which is the bug fix)
- Spring Boot exposes `/api/subjects`, `/api/studies`, `/api/series`,
  `/api/instances` (each instance row carries both `filePath` and
  `deidFilePath`), `/api/devices` (with `subjectCount` and
  `instanceCount` aggregates), plus `/orthanc/**` proxy and `/api/health`
- DICOM parser FastAPI exposes `GET /api/instances/{id}/preview?variant=raw|deid`
  which returns a windowed PNG preview rendered from the on-disk file
- React frontend with the QuantixMed theme:
  - Dashboard with recharts pie charts and a real PACS device table
  - Subject detail page with **expand/collapse series rows** and a
    **side-by-side raw + de-identified image carousel** for each series
  - Full OHIF Viewer integration via the Spring Boot proxy

## How the carousel works

On the Subject Detail page, each series row collapses by default. Click
to expand → the React side fetches `/api/series/{seriesId}/instances`
from Spring Boot, which returns each instance with both `filePath` and
`deidFilePath`. The carousel then renders two `<img>` tags side-by-side:

```
http://localhost:8000/api/instances/{instanceId}/preview?variant=raw
http://localhost:8000/api/instances/{instanceId}/preview?variant=deid
```

Both URLs hit the parser FastAPI, which loads the corresponding DICOM
from disk, applies Rescale Slope/Intercept and DICOM window/level,
inverts MONOCHROME1, normalizes to 8-bit greyscale, and returns a PNG
(cached for 1 hour). Prev/Next buttons walk through all instances in
the series in `instanceNumber` order.

For the full interactive viewer with measurement tools, scroll, etc.,
click "Open in OHIF" on a study card → the OHIF Viewer loads the
de-identified study from Orthanc through the Spring Boot DICOMweb proxy.
