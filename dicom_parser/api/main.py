"""
QuantixMed DICOM Parser API (v2 — Orthanc-integrated, JPEG-free)
All image viewing is delegated to Orthanc + OHIF Viewer.
"""
import logging
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, UploadFile, File, Form, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from config.settings import DATA_DIR, ORTHANC_URL
from models.database import init_pool, init_schema, get_conn
from parsers.dicom_parser import (
    register_pacs_device, load_subjects_csv,
    process_subject, process_zip_file,
)
from parsers.orthanc_client import is_orthanc_available, get_orthanc_study_id

logging.basicConfig(level=logging.INFO, format="%(levelname)s | %(name)s | %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(
    title="QuantixMed DICOM Parser API",
    version="2.0.0",
    description="DICOM 2026 | HIPAA/FDA | Orthanc + OHIF integration",
)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


@app.on_event("startup")
def startup():
    init_pool()
    init_schema()
    logger.info("QuantixMed DICOM API v2 started")


# ─── Pydantic Models ──────────────────────────────────────────────────────────

class DeviceCreate(BaseModel):
    name: str
    serial_number: str
    modality: str = "MULTI"
    status: str = "Active"
    location: str = ""
    manufacturer: str = ""
    model: str = ""
    ip_address: str = ""
    port: int = 11112
    ae_title: str = ""
    description: str = ""


# ─── PACS Devices ─────────────────────────────────────────────────────────────

@app.post("/api/devices", tags=["PACS Devices"])
def create_device(device: DeviceCreate):
    dev_id = register_pacs_device(**device.dict())
    return {"id": dev_id, "message": "Device registered"}

@app.get("/api/devices", tags=["PACS Devices"])
def list_devices():
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT d.*,
                    (SELECT COUNT(*) FROM subjects s WHERE s.device_id=d.id) AS subject_count
                FROM pacs_devices d ORDER BY d.created_at DESC
            """)
            return [dict(r) for r in cur.fetchall()]

@app.get("/api/devices/{device_id}", tags=["PACS Devices"])
def get_device(device_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM pacs_devices WHERE id=%s", (device_id,))
            row = cur.fetchone()
    if not row: raise HTTPException(404, "Device not found")
    return dict(row)


# ─── Subjects ─────────────────────────────────────────────────────────────────

@app.get("/api/devices/{device_id}/subjects", tags=["Subjects"])
def list_subjects(device_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT s.*, p.patient_sex, p.patient_age, p.patient_weight
                FROM subjects s
                LEFT JOIN patients p ON p.subject_id=s.id
                WHERE s.device_id=%s ORDER BY s.created_at DESC
            """, (device_id,))
            return [dict(r) for r in cur.fetchall()]

@app.get("/api/subjects/{subject_id}", tags=["Subjects"])
def get_subject(subject_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM subjects WHERE id=%s", (subject_id,))
            row = cur.fetchone()
    if not row: raise HTTPException(404, "Subject not found")
    return dict(row)

@app.post("/api/subjects/upload", tags=["Subjects"])
async def upload_subject(
    background_tasks: BackgroundTasks,
    device_id: str = Form(...),
    zip_file: UploadFile = File(...),
    csv_file: Optional[UploadFile] = File(None),
):
    zip_path = DATA_DIR / zip_file.filename
    with open(zip_path, "wb") as f: f.write(await zip_file.read())
    csv_path = None
    if csv_file:
        csv_path = DATA_DIR / csv_file.filename
        with open(csv_path, "wb") as f: f.write(await csv_file.read())
    background_tasks.add_task(_bg_process, device_id, zip_path, csv_path)
    return {"message": "Accepted — processing in background"}

def _bg_process(device_id, zip_path, csv_path):
    try:
        process_zip_file(device_id, zip_path, csv_path)
    except Exception as e:
        logger.error("Background processing failed: %s", e)

@app.post("/api/subjects/process-local", tags=["Subjects"])
def process_local(device_id: str, subject_path: str, csv_path: Optional[str] = None):
    subject_dir = Path(subject_path)
    if not subject_dir.exists(): raise HTTPException(400, f"Path not found: {subject_path}")
    csv_meta = load_subjects_csv(Path(csv_path)) if csv_path else {}
    return process_subject(device_id, subject_dir, csv_meta.get(subject_dir.name))


# ─── Studies ──────────────────────────────────────────────────────────────────

@app.get("/api/subjects/{subject_id}/studies", tags=["Studies"])
def list_studies(subject_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT * FROM studies WHERE subject_id=%s ORDER BY study_date
            """, (subject_id,))
            return [dict(r) for r in cur.fetchall()]

@app.get("/api/studies/{study_id}", tags=["Studies"])
def get_study(study_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM studies WHERE id=%s", (study_id,))
            row = cur.fetchone()
    if not row: raise HTTPException(404, "Study not found")
    return dict(row)


# ─── Series ───────────────────────────────────────────────────────────────────

@app.get("/api/studies/{study_id}/series", tags=["Series"])
def list_series(study_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT * FROM series WHERE study_id=%s ORDER BY series_number
            """, (study_id,))
            return [dict(r) for r in cur.fetchall()]

@app.get("/api/series/{series_id}", tags=["Series"])
def get_series(series_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM series WHERE id=%s", (series_id,))
            row = cur.fetchone()
    if not row: raise HTTPException(404, "Series not found")
    return dict(row)


# ─── Instances ────────────────────────────────────────────────────────────────

@app.get("/api/series/{series_id}/instances", tags=["Instances"])
def list_instances(series_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT * FROM instances WHERE series_id=%s ORDER BY instance_number
            """, (series_id,))
            return [dict(r) for r in cur.fetchall()]


# ─── DICOM → PNG rendering ────────────────────────────────────────────────────
# Used by the React UI to show raw + de-identified previews side-by-side.
# Loads either the raw or de-identified DICOM from disk, applies window/level,
# normalises to 8-bit greyscale, and returns a PNG.

@app.get("/api/instances/{instance_id}/preview", tags=["Instances"])
def instance_preview(instance_id: str, variant: str = "raw", max_size: int = 512):
    """
    variant: 'raw' → file_path, 'deid' → deid_file_path.
    Returns image/png bytes.
    """
    if variant not in ("raw", "deid"):
        raise HTTPException(400, "variant must be 'raw' or 'deid'")
    col = "file_path" if variant == "raw" else "deid_file_path"

    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(f"SELECT {col} AS p FROM instances WHERE id=%s::uuid", (instance_id,))
            row = cur.fetchone()
    if not row or not row.get("p"):
        raise HTTPException(404, f"No {variant} file recorded for instance {instance_id}")

    file_path = Path(row["p"])
    if not file_path.exists():
        raise HTTPException(404, f"File not found on disk: {file_path}")

    try:
        import io
        import numpy as np
        import pydicom
        from PIL import Image
        from fastapi.responses import Response

        ds = pydicom.dcmread(str(file_path))
        arr = ds.pixel_array.astype("float32")
        # Apply Rescale Slope/Intercept if present (CT, PET)
        slope = float(getattr(ds, "RescaleSlope", 1) or 1)
        intercept = float(getattr(ds, "RescaleIntercept", 0) or 0)
        arr = arr * slope + intercept

        modality = str(getattr(ds, "Modality", "") or "").upper()
        units    = str(getattr(ds, "Units", "") or "").upper()

        # ── Window/level selection ─────────────────────────────────────
        # PET (or anything in BQML/SUV units): the DICOM-supplied W/L often
        # spans the full sensor range, which makes everything but the
        # brightest hot-spot black. Use percentile windowing instead.
        # CT and others: prefer the DICOM-supplied W/L when present.
        is_pet = (modality in ("PT", "PET")) or units in ("BQML", "CNTS", "SUV")

        if is_pet:
            nz = arr[arr > 0]
            if nz.size > 0:
                lo = float(np.percentile(nz,  1.0))
                hi = float(np.percentile(nz, 99.0))
            else:
                lo, hi = float(arr.min()), float(arr.max())
        else:
            wc = getattr(ds, "WindowCenter", None)
            ww = getattr(ds, "WindowWidth", None)
            if wc is not None and ww is not None:
                wc = float(wc[0] if hasattr(wc, "__iter__") and not isinstance(wc, str) else wc)
                ww = float(ww[0] if hasattr(ww, "__iter__") and not isinstance(ww, str) else ww)
                lo, hi = wc - ww / 2, wc + ww / 2
            else:
                lo, hi = float(arr.min()), float(arr.max())

        if hi <= lo:
            hi = lo + 1
        arr = np.clip((arr - lo) / (hi - lo) * 255.0, 0, 255).astype("uint8")
        # MONOCHROME1 means inverted greyscale
        if str(getattr(ds, "PhotometricInterpretation", "")).strip() == "MONOCHROME1":
            arr = 255 - arr

        img = Image.fromarray(arr, mode="L")
        if max(img.size) > max_size:
            img.thumbnail((max_size, max_size))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return Response(content=buf.getvalue(), media_type="image/png",
                        headers={"Cache-Control": "public, max-age=3600"})
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("preview failed for %s", file_path)
        raise HTTPException(500, f"Preview render error: {e}")


# ─── Orthanc proxy / status ───────────────────────────────────────────────────

@app.get("/api/orthanc/status", tags=["Orthanc"])
def orthanc_status():
    available = is_orthanc_available()
    return {"available": available, "url": ORTHANC_URL}

@app.get("/api/orthanc/study/{orthanc_study_id}/viewer-url", tags=["Orthanc"])
def get_viewer_url(orthanc_study_id: str):
    """Returns the OHIF Viewer deep-link URL for a study."""
    wado_base = f"{ORTHANC_URL}/dicom-web"
    ohif_url  = f"http://localhost:3000/viewer?StudyInstanceUIDs={orthanc_study_id}&wadoRsRoot={wado_base}"
    return {"ohif_viewer_url": ohif_url, "wado_rs_root": wado_base, "orthanc_study_id": orthanc_study_id}


# ─── Compliance audit ─────────────────────────────────────────────────────────

@app.get("/api/audit/{entity_type}/{entity_id}", tags=["Compliance"])
def get_audit(entity_type: str, entity_id: str):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT * FROM deid_audit_log
                WHERE entity_type=%s AND entity_id=%s::uuid ORDER BY processed_at
            """, (entity_type, entity_id))
            return [dict(r) for r in cur.fetchall()]


# ─── Health ───────────────────────────────────────────────────────────────────

@app.get("/api/health", tags=["System"])
def health():
    return {"status": "ok", "version": "2.0.0", "orthanc": ORTHANC_URL}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("api.main:app", host="0.0.0.0", port=8000, reload=True)
