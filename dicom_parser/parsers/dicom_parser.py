"""
QuantixMed — Core DICOM Parser (v2 — JPEG-free, Orthanc-integrated)
Walks subject folder hierarchy, de-identifies, uploads to Orthanc,
and populates PostgreSQL with metadata + Orthanc IDs for OHIF Viewer.
"""
import csv
import logging
import uuid
import zipfile
from pathlib import Path
from typing import Optional

import pydicom

from config.settings import DATA_DIR, ORTHANC_AUTO_UPLOAD
from deidentifier.deidentifier import DicomDeidentifier
from models.database import get_conn
from parsers.orthanc_client import (
    upload_dicom_file, get_orthanc_study_id,
    get_orthanc_series_id, is_orthanc_available,
    get_orthanc_study_main_uid,
)

logger = logging.getLogger(__name__)


# ── Safe value helpers ────────────────────────────────────────────────────────

def _safe_str(val, max_len: int = 255) -> Optional[str]:
    if val is None: return None
    s = str(val).strip()
    return s[:max_len] if s else None

def _safe_float(val) -> Optional[float]:
    try:
        if hasattr(val, "__iter__") and not isinstance(val, str): val = val[0]
        return float(val)
    except Exception: return None

def _safe_int(val) -> Optional[int]:
    try:
        if hasattr(val, "__iter__") and not isinstance(val, str): val = val[0]
        return int(val)
    except Exception: return None

def _da_to_date(da_str) -> Optional[str]:
    if not da_str: return None
    s = str(da_str).strip()
    if len(s) < 8: return None
    try: return f"{s[:4]}-{s[4:6]}-{s[6:8]}"
    except Exception: return None

def _tm_to_time(tm_str) -> Optional[str]:
    if not tm_str: return None
    s = str(tm_str).strip().split(".")[0]
    if len(s) >= 6: return f"{s[:2]}:{s[2:4]}:{s[4:6]}"
    if len(s) >= 4: return f"{s[:2]}:{s[2:4]}:00"
    return None


# ── PACS Device ───────────────────────────────────────────────────────────────

def register_pacs_device(name, serial_number, modality="MULTI", status="Active",
                          location="", manufacturer="", model="",
                          ip_address="", port=11112, ae_title="", description="") -> str:
    sql = """
        INSERT INTO pacs_devices
            (id, name, serial_number, modality, status, location,
             manufacturer, model, ip_address, port, ae_title, description)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        ON CONFLICT (serial_number) DO UPDATE SET
            name=EXCLUDED.name, modality=EXCLUDED.modality,
            status=EXCLUDED.status, location=EXCLUDED.location,
            manufacturer=EXCLUDED.manufacturer, model=EXCLUDED.model,
            ip_address=EXCLUDED.ip_address, port=EXCLUDED.port,
            ae_title=EXCLUDED.ae_title, description=EXCLUDED.description,
            updated_at=NOW()
        RETURNING id
    """
    dev_id = str(uuid.uuid4())
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (dev_id, name, serial_number, modality, status,
                              location, manufacturer, model, ip_address, port,
                              ae_title, description))
            dev_id = str(cur.fetchone()["id"])
    logger.info("PACS device registered: %s (%s)", name, dev_id)
    return dev_id


# ── CSV metadata ─────────────────────────────────────────────────────────────

def load_subjects_csv(csv_path: Path) -> dict:
    subjects: dict = {}
    with open(csv_path, encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            pid = row.get("PatientID", "").strip()
            if pid and pid not in subjects:
                subjects[pid] = row
    logger.info("CSV loaded: %d unique subjects", len(subjects))
    return subjects


# ── Subject ───────────────────────────────────────────────────────────────────

def upsert_subject(device_id, subject_id, csv_row, data_root_path) -> str:
    r = csv_row or {}
    deid_id = DicomDeidentifier._make_surrogate_id(subject_id)
    sql = """
        INSERT INTO subjects
            (id, device_id, subject_id, collection, site,
             species_description, is_phantom, data_root_path,
             deid_subject_id, load_status)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,'Processing')
        ON CONFLICT (subject_id) DO UPDATE SET
            device_id=EXCLUDED.device_id, collection=EXCLUDED.collection,
            site=EXCLUDED.site, data_root_path=EXCLUDED.data_root_path,
            deid_subject_id=EXCLUDED.deid_subject_id,
            load_status='Processing', updated_at=NOW()
        RETURNING id
    """
    subj_id = str(uuid.uuid4())
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (
                subj_id, device_id, subject_id,
                _safe_str(r.get("Collection", "")),
                _safe_str(r.get("Site", "")),
                _safe_str(r.get("SpeciesDescription", "")),
                r.get("Phantom", "NO").upper() == "YES",
                data_root_path, deid_id,
            ))
            subj_id = str(cur.fetchone()["id"])
    return subj_id


def _mark_subject_loaded(subj_id, n_studies, n_series, n_instances):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE subjects SET load_status='Loaded',
                    total_studies=%s, total_series=%s, total_instances=%s, updated_at=NOW()
                WHERE id=%s
            """, (n_studies, n_series, n_instances, subj_id))


# ── Patient ───────────────────────────────────────────────────────────────────

def upsert_patient(subject_db_id, ds) -> str:
    deid = DicomDeidentifier(str(getattr(ds, "PatientID", "")))
    sql = """
        INSERT INTO patients
            (id, subject_id, dicom_patient_id, patient_sex, patient_age,
             patient_weight, patient_size, ethnic_group,
             patient_identity_removed, deidentification_method, deid_patient_id)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        ON CONFLICT DO NOTHING RETURNING id
    """
    pat_id = str(uuid.uuid4())
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (
                pat_id, subject_db_id,
                _safe_str(getattr(ds, "PatientID", None)),
                _safe_str(getattr(ds, "PatientSex", None)),
                _safe_str(getattr(ds, "PatientAge", None)),
                _safe_float(getattr(ds, "PatientWeight", None)),
                _safe_float(getattr(ds, "PatientSize", None)),
                _safe_str(getattr(ds, "EthnicGroup", None)),
                _safe_str(getattr(ds, "PatientIdentityRemoved", None)),
                _safe_str(getattr(ds, "DeidentificationMethod", None)),
                deid.surrogate_patient_id,
            ))
            row = cur.fetchone()
            if row: pat_id = str(row["id"])
    return pat_id


# ── Study ─────────────────────────────────────────────────────────────────────

def upsert_study(subject_db_id, ds, deid: DicomDeidentifier) -> str:
    study_uid = _safe_str(getattr(ds, "StudyInstanceUID", None))
    if not study_uid: raise ValueError("Missing StudyInstanceUID")
    deid_uid  = deid._get_surrogate_uid(study_uid)
    orig_date = _da_to_date(getattr(ds, "StudyDate", None))
    deid_date = _da_to_date(deid._offset_date(str(getattr(ds, "StudyDate", "") or "")))
    sql = """
        INSERT INTO studies
            (id, subject_id, study_instance_uid, study_date, study_time,
             study_description, study_id, accession_number,
             admitting_diagnosis, deid_study_instance_uid, deid_study_date)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        ON CONFLICT (study_instance_uid) DO UPDATE SET
            subject_id=EXCLUDED.subject_id,
            study_description=EXCLUDED.study_description, updated_at=NOW()
        RETURNING id
    """
    study_id = str(uuid.uuid4())
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (
                study_id, subject_db_id, study_uid,
                orig_date, _tm_to_time(getattr(ds, "StudyTime", None)),
                _safe_str(getattr(ds, "StudyDescription", None)),
                _safe_str(getattr(ds, "StudyID", None)),
                _safe_str(getattr(ds, "AccessionNumber", None)),
                _safe_str(getattr(ds, "AdmittingDiagnosesDescription", None)),
                deid_uid, deid_date,
            ))
            study_id = str(cur.fetchone()["id"])
    return study_id


def update_study_orthanc(study_db_id, orthanc_study_id, deid_study_uid):
    """Store Orthanc study ID and WADO URL in DB after upload."""
    from config.settings import ORTHANC_URL
    wado_url = f"{ORTHANC_URL}/dicom-web/studies/{deid_study_uid}"
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE studies SET
                    orthanc_study_id=%s, orthanc_study_uid=%s,
                    wado_study_url=%s, updated_at=NOW()
                WHERE id=%s
            """, (orthanc_study_id, deid_study_uid, wado_url, study_db_id))


def update_study_counts(study_db_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE studies SET
                    total_series=(SELECT COUNT(*) FROM series WHERE study_id=%s),
                    total_instances=(SELECT COUNT(*) FROM instances i
                                     JOIN series s ON s.id=i.series_id WHERE s.study_id=%s),
                    updated_at=NOW()
                WHERE id=%s
            """, (study_db_id, study_db_id, study_db_id))


# ── Series ────────────────────────────────────────────────────────────────────

def upsert_series(study_db_id, ds, deid: DicomDeidentifier) -> str:
    series_uid = _safe_str(getattr(ds, "SeriesInstanceUID", None))
    if not series_uid: raise ValueError("Missing SeriesInstanceUID")
    deid_uid  = deid._get_surrogate_uid(series_uid)
    ps        = getattr(ds, "PixelSpacing", None)
    ps_row    = _safe_float(ps[0]) if ps else None
    ps_col    = _safe_float(ps[1]) if ps and len(ps) > 1 else ps_row
    orig_date = _da_to_date(getattr(ds, "SeriesDate", None))
    deid_date = _da_to_date(deid._offset_date(str(getattr(ds, "SeriesDate", "") or "")))
    sql = """
        INSERT INTO series
            (id, study_id, series_instance_uid, series_number, series_date,
             series_time, series_description, modality, body_part_examined,
             protocol_name, manufacturer, manufacturer_model, software_versions,
             pixel_spacing_row, pixel_spacing_col, slice_thickness,
             patient_position, deid_series_instance_uid, deid_series_date)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        ON CONFLICT (series_instance_uid) DO UPDATE SET
            study_id=EXCLUDED.study_id, updated_at=NOW()
        RETURNING id
    """
    ser_id = str(uuid.uuid4())
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (
                ser_id, study_db_id, series_uid,
                _safe_int(getattr(ds, "SeriesNumber", None)),
                orig_date, _tm_to_time(getattr(ds, "SeriesTime", None)),
                _safe_str(getattr(ds, "SeriesDescription", None)),
                _safe_str(getattr(ds, "Modality", None)),
                _safe_str(getattr(ds, "BodyPartExamined", None)),
                _safe_str(getattr(ds, "ProtocolName", None)),
                _safe_str(getattr(ds, "Manufacturer", None)),
                _safe_str(getattr(ds, "ManufacturerModelName", None)),
                _safe_str(getattr(ds, "SoftwareVersions", None)),
                ps_row, ps_col,
                _safe_float(getattr(ds, "SliceThickness", None)),
                _safe_str(getattr(ds, "PatientPosition", None)),
                deid_uid, deid_date,
            ))
            ser_id = str(cur.fetchone()["id"])
    return ser_id


def update_series_orthanc(series_db_id, orthanc_series_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE series SET orthanc_series_id=%s, updated_at=NOW() WHERE id=%s",
                (orthanc_series_id, series_db_id)
            )


def increment_series_count(series_db_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE series SET image_count=image_count+1, updated_at=NOW() WHERE id=%s",
                (series_db_id,)
            )


# ── Instance ──────────────────────────────────────────────────────────────────

def insert_instance(series_db_id, ds, file_path, file_size, deid: DicomDeidentifier) -> str:
    sop_uid  = _safe_str(getattr(ds, "SOPInstanceUID", None)) or str(uuid.uuid4())
    deid_uid = deid._get_surrogate_uid(sop_uid)
    sql = """
        INSERT INTO instances
            (id, series_id, sop_instance_uid, sop_class_uid, instance_number,
             acquisition_number, acquisition_date, acquisition_time,
             content_date, content_time, rows, cols,
             bits_allocated, bits_stored, samples_per_pixel, photometric_interp,
             slice_location, file_path, file_size_bytes, deid_sop_instance_uid)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        ON CONFLICT (sop_instance_uid) DO NOTHING RETURNING id
    """
    inst_id = str(uuid.uuid4())
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (
                inst_id, series_db_id, sop_uid,
                _safe_str(getattr(ds, "SOPClassUID", None)),
                _safe_int(getattr(ds, "InstanceNumber", None)),
                _safe_int(getattr(ds, "AcquisitionNumber", None)),
                _da_to_date(getattr(ds, "AcquisitionDate", None)),
                _tm_to_time(getattr(ds, "AcquisitionTime", None)),
                _da_to_date(getattr(ds, "ContentDate", None)),
                _tm_to_time(getattr(ds, "ContentTime", None)),
                _safe_int(getattr(ds, "Rows", None)),
                _safe_int(getattr(ds, "Columns", None)),
                _safe_int(getattr(ds, "BitsAllocated", None)),
                _safe_int(getattr(ds, "BitsStored", None)),
                _safe_int(getattr(ds, "SamplesPerPixel", None)),
                _safe_str(getattr(ds, "PhotometricInterpretation", None)),
                _safe_float(getattr(ds, "SliceLocation", None)),
                file_path, file_size, deid_uid,
            ))
            row = cur.fetchone()
            if row: inst_id = str(row["id"])
    return inst_id


def update_instance_orthanc(instance_db_id, orthanc_instance_id):
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE instances SET orthanc_instance_id=%s, updated_at=NOW() WHERE id=%s",
                (orthanc_instance_id, instance_db_id)
            )


def update_instance_deid_path(instance_db_id, deid_file_path):
    """Persist the on-disk path of the de-identified DICOM file."""
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "UPDATE instances SET deid_file_path=%s, updated_at=NOW() WHERE id=%s",
                (str(deid_file_path), instance_db_id)
            )


# ── Main pipeline ─────────────────────────────────────────────────────────────

def process_subject(device_id, subject_dir: Path, csv_metadata=None,
                     default_collection: str = "TCIA PSMA-PET-CT-Lesions",
                     default_site: str = "Unknown",
                     default_species: str = "Homo sapiens") -> dict:
    """
    Full pipeline for one subject:
    1. De-identify all DICOM files
    2. Upload de-identified files to Orthanc
    3. Store metadata + Orthanc IDs in PostgreSQL

    If csv_metadata is None or missing fields, Collection / Site / Species
    are derived from the DICOM headers (or default_* values as a last resort).
    """
    subject_id = subject_dir.name
    logger.info("Processing subject: %s", subject_id)
    deid = DicomDeidentifier(subject_id)

    orthanc_ok = ORTHANC_AUTO_UPLOAD and is_orthanc_available()
    if not orthanc_ok:
        logger.warning("Orthanc not available — metadata-only mode (no viewer)")

    # ── Derive subject-level metadata from the first DICOM in the tree ────
    # CSV (if any) takes precedence; missing fields fall back to DICOM tags;
    # missing DICOM tags fall back to the CLI defaults.
    derived_meta = dict(csv_metadata or {})
    try:
        first = next(subject_dir.rglob("*.dcm"))
        ds0 = pydicom.dcmread(str(first), stop_before_pixels=True, force=True)
        if not derived_meta.get("Collection"):
            derived_meta["Collection"] = (
                _safe_str(getattr(ds0, "ClinicalTrialProtocolName", None))
                or _safe_str(getattr(ds0, "ClinicalTrialSponsorName", None))
                or default_collection
            )
        if not derived_meta.get("Site"):
            derived_meta["Site"] = (
                _safe_str(getattr(ds0, "ClinicalTrialSiteName", None))
                or _safe_str(getattr(ds0, "InstitutionName", None))
                or default_site
            )
        if not derived_meta.get("SpeciesDescription"):
            derived_meta["SpeciesDescription"] = (
                _safe_str(getattr(ds0, "PatientSpeciesDescription", None))
                or default_species
            )
    except StopIteration:
        logger.warning("No DICOM files in %s", subject_dir)

    subj_db_id = upsert_subject(device_id, subject_id, derived_meta, str(subject_dir))

    study_ids:  dict[str, str] = {}
    series_ids: dict[str, str] = {}
    patient_registered = False
    stats = {"studies": 0, "series": 0, "instances": 0}

    # Map original UID -> Orthanc internal ID, captured from upload response.
    # This is more reliable than tools/lookup which can race.
    orthanc_study_internal: dict[str, str] = {}   # original study_uid -> Orthanc internal study UUID
    orthanc_series_internal: dict[str, str] = {}  # original series_uid -> Orthanc internal series UUID

    for dcm_path in sorted(subject_dir.rglob("*.dcm")):
        try:
            ds = pydicom.dcmread(str(dcm_path), force=True)
        except Exception as e:
            logger.warning("Cannot read %s: %s", dcm_path, e)
            continue

        if not patient_registered:
            upsert_patient(subj_db_id, ds)
            patient_registered = True

        study_uid = _safe_str(getattr(ds, "StudyInstanceUID", None))
        if not study_uid: continue

        if study_uid not in study_ids:
            study_ids[study_uid] = upsert_study(subj_db_id, ds, deid)
            stats["studies"] += 1

        series_uid = _safe_str(getattr(ds, "SeriesInstanceUID", None))
        if not series_uid: continue

        if series_uid not in series_ids:
            series_ids[series_uid] = upsert_series(study_ids[study_uid], ds, deid)
            stats["series"] += 1

        series_db_id = series_ids[series_uid]
        increment_series_count(series_db_id)

        inst_db_id = insert_instance(
            series_db_id, ds, str(dcm_path), dcm_path.stat().st_size, deid
        )
        stats["instances"] += 1

        # ── Always de-identify (independent of Orthanc availability) ──
        try:
            deid_path = deid.deidentify_file(dcm_path)
            update_instance_deid_path(inst_db_id, deid_path)
        except Exception as e:
            logger.warning("De-id failed for %s: %s", dcm_path.name, e)
            deid_path = None

        # ── Upload de-identified file to Orthanc (optional) ───────────
        if orthanc_ok and deid_path is not None:
            try:
                result = upload_dicom_file(deid_path)
                if result:
                    update_instance_orthanc(inst_db_id, result.get("ID"))
                    # Capture parent IDs from the upload response — these are
                    # the canonical Orthanc internal UUIDs for the study/series
                    # that just received this instance.
                    if result.get("ParentStudy"):
                        orthanc_study_internal[study_uid] = result["ParentStudy"]
                    if result.get("ParentSeries"):
                        orthanc_series_internal[series_uid] = result["ParentSeries"]
            except Exception as e:
                logger.warning("Orthanc upload failed for %s: %s", dcm_path.name, e)

    # ── Post-process: persist Orthanc IDs + verified DICOM UIDs ───────
    if orthanc_ok:
        for study_uid, study_db_id in study_ids.items():
            orth_study_id = orthanc_study_internal.get(study_uid)
            if not orth_study_id:
                # Fallback: lookup by surrogate UID
                deid_uid_guess = deid._get_surrogate_uid(study_uid)
                orth_study_id = get_orthanc_study_id(deid_uid_guess)
            if orth_study_id:
                # Query Orthanc for the actual StudyInstanceUID it has stored
                # — that's the UID OHIF/QIDO must use.
                actual_uid = get_orthanc_study_main_uid(orth_study_id) or deid._get_surrogate_uid(study_uid)
                update_study_orthanc(study_db_id, orth_study_id, actual_uid)
                logger.info("Study linked to Orthanc: %s (UID=%s)", orth_study_id, actual_uid)
            else:
                logger.warning("Could not link study %s to Orthanc", study_uid)

        for series_uid, series_db_id in series_ids.items():
            orth_series_id = orthanc_series_internal.get(series_uid)
            if not orth_series_id:
                deid_uid_guess = deid._get_surrogate_uid(series_uid)
                orth_series_id = get_orthanc_series_id(deid_uid_guess)
            if orth_series_id:
                update_series_orthanc(series_db_id, orth_series_id)

    for study_db_id in study_ids.values():
        update_study_counts(study_db_id)

    _mark_subject_loaded(subj_db_id, stats["studies"], stats["series"], stats["instances"])
    logger.info("Subject %s done: %d studies / %d series / %d instances",
                subject_id, stats["studies"], stats["series"], stats["instances"])
    return {"subject_db_id": subj_db_id, **stats}


def process_zip_file(device_id, zip_path: Path, csv_path=None) -> list:
    extract_dir = DATA_DIR / zip_path.stem
    extract_dir.mkdir(parents=True, exist_ok=True)
    csv_meta = load_subjects_csv(csv_path) if csv_path and Path(csv_path).exists() else {}
    with zipfile.ZipFile(str(zip_path), "r") as zf:
        zf.extractall(str(extract_dir))
    results = []
    for subject_dir in sorted(extract_dir.iterdir()):
        if subject_dir.is_dir() and not subject_dir.name.startswith("."):
            results.append(process_subject(device_id, subject_dir, csv_meta.get(subject_dir.name)))
    return results
