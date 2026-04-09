# QuantixMed DICOM Parser

Python-based DICOM parser and de-identifier.  
**Standards:** DICOM PS3.15 2026 Annex E | HIPAA §164.514 | FDA 21 CFR Part 11

---

## Python Version Compatibility

| Python | Status | Notes |
|--------|--------|-------|
| 3.9 – 3.12 | ✅ Fully tested | All wheels available |
| 3.13 | ✅ Works | All wheels available |
| 3.14 | ✅ Works | Use **latest** package versions (not pinned old ones) |

> **Windows + Python 3.14 note:** The old pinned versions (`pandas==2.2.2`, `numpy==1.26.4`,
> `pydicom==2.4.4`) do **not** ship pre-built wheels for Python 3.14 and will
> try to compile from source — which requires a full C/C++ toolchain.
> The updated `requirements.txt` uses unpinned `>=` ranges so pip will
> automatically pick the latest version that has a `cp314-win_amd64` wheel.

---

## Quick Start

### 1. Prerequisites

```bash
# Upgrade pip first — older pip may resolve wrong wheel versions
python -m pip install --upgrade pip

# Install all dependencies (pre-built wheels, no compilation needed)
pip install -r requirements.txt
```

PostgreSQL running locally (or update environment variables):
```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=quantixmed_dicom
DB_USER=postgres
DB_PASSWORD=postgres
```

Create the database:
```sql
CREATE DATABASE quantixmed_dicom;
```

### 2. Run the pipeline (CLI)
```bash
cd dicom_parser
python run_pipeline.py \
  --psma-dir ../psma_data \
  --device-name "PSMA-PET-CT PACS" \
  --serial "PSMA-PET-2026-001"
```

### 3. Start the REST API
```bash
cd dicom_parser
uvicorn api.main:app --host 0.0.0.0 --port 8000 --reload
```

Swagger UI: http://localhost:8000/docs

---

## Project Structure

```
dicom_parser/
├── config/
│   └── settings.py          # DB, paths, de-identification action table
├── models/
│   ├── schema.sql            # PostgreSQL DDL
│   └── database.py           # Connection pool, schema init
├── parsers/
│   ├── dicom_parser.py       # Core pipeline: subject→study→series→instance
│   └── image_processor.py    # DICOM→JPEG + collage generation
├── deidentifier/
│   └── deidentifier.py       # DICOM PS3.15 Annex E de-identification
├── api/
│   └── main.py               # FastAPI REST endpoints
├── run_pipeline.py            # CLI batch runner
└── requirements.txt
```

---

## Database Schema Hierarchy

```
pacs_devices
  └── subjects          (device_id → pacs_devices.id)
        └── patients    (subject_id → subjects.id)
        └── studies     (subject_id → subjects.id)
              └── series         (study_id → studies.id)
                    └── instances      (series_id → series.id)
                    └── dicom_images   (series_id → series.id)
                    └── jpeg_dicom     (series_id → series.id)
              └── study_collages (study_id → studies.id)
  └── deid_audit_log    (compliance trail)
```

---

## De-identification Rules (DICOM PS3.15 Annex E)

| Action | Meaning |
|--------|---------|
| `K`    | Keep unchanged |
| `X`    | Remove entirely |
| `Z`    | Zero-length / anonymised |
| `D`    | Replace with dummy / offset date |
| `U`    | Replace UID with generated surrogate |
| `C`    | Clean text field |

All actions logged to `deid_audit_log` table for HIPAA compliance.

---

## REST API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/devices` | Register PACS device |
| GET  | `/api/devices` | List devices |
| GET  | `/api/devices/{id}/subjects` | Subjects for device |
| GET  | `/api/subjects/{id}/studies` | Studies for subject |
| GET  | `/api/studies/{id}/series` | Series for study |
| GET  | `/api/series/{id}/instances` | Instances |
| GET  | `/api/series/{id}/images` | DICOM images + JPEG URLs |
| GET  | `/api/images/jpeg/{filename}` | Serve JPEG |
| GET  | `/api/images/collage/{filename}` | Serve collage |
| POST | `/api/subjects/upload` | Upload ZIP (async) |
