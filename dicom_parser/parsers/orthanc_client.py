"""
QuantixMed — Orthanc REST Client
Uploads de-identified DICOM files to Orthanc and stores the Orthanc IDs in PostgreSQL.
Orthanc exposes DICOMweb (WADO-RS/STOW-RS/QIDO-RS) used directly by OHIF Viewer.
"""
import logging
import requests
from pathlib import Path
from typing import Optional

from config.settings import ORTHANC_URL, ORTHANC_USER, ORTHANC_PASSWORD

logger = logging.getLogger(__name__)

_SESSION: Optional[requests.Session] = None


def _session() -> requests.Session:
    global _SESSION
    if _SESSION is None:
        _SESSION = requests.Session()
        _SESSION.auth = (ORTHANC_USER, ORTHANC_PASSWORD)
        _SESSION.headers.update({"Accept": "application/json"})
    return _SESSION


def is_orthanc_available() -> bool:
    """Quick health check — returns True if Orthanc is reachable."""
    try:
        r = _session().get(f"{ORTHANC_URL}/system", timeout=5)
        return r.status_code == 200
    except Exception as e:
        logger.warning("Orthanc not reachable at %s: %s", ORTHANC_URL, e)
        return False


def upload_dicom_file(dcm_path: Path) -> Optional[dict]:
    """
    Upload a single DICOM file to Orthanc via /instances endpoint.
    Returns Orthanc response dict with 'ID', 'ParentSeries', 'ParentStudy', 'ParentPatient'.
    Returns None on failure.
    """
    try:
        with open(dcm_path, "rb") as f:
            data = f.read()
        r = _session().post(
            f"{ORTHANC_URL}/instances",
            data=data,
            headers={"Content-Type": "application/dicom"},
            timeout=60,
        )
        if r.status_code in (200, 201):
            result = r.json()
            logger.debug("Uploaded %s → Orthanc instance %s", dcm_path.name, result.get("ID"))
            return result
        else:
            logger.warning("Orthanc upload failed %s: HTTP %d %s", dcm_path.name, r.status_code, r.text[:200])
            return None
    except Exception as e:
        logger.error("Orthanc upload error %s: %s", dcm_path.name, e)
        return None


def get_orthanc_study_id(study_instance_uid: str) -> Optional[str]:
    """Look up Orthanc internal study ID by DICOM StudyInstanceUID."""
    try:
        r = _session().post(
            f"{ORTHANC_URL}/tools/lookup",
            json=study_instance_uid,
            timeout=10,
        )
        if r.status_code == 200:
            results = r.json()
            for item in results:
                if item.get("Type") == "Study":
                    return item.get("ID")
    except Exception as e:
        logger.warning("Orthanc lookup error: %s", e)
    return None


def get_orthanc_series_id(series_instance_uid: str) -> Optional[str]:
    """Look up Orthanc internal series ID by DICOM SeriesInstanceUID."""
    try:
        r = _session().post(
            f"{ORTHANC_URL}/tools/lookup",
            json=series_instance_uid,
            timeout=10,
        )
        if r.status_code == 200:
            results = r.json()
            for item in results:
                if item.get("Type") == "Series":
                    return item.get("ID")
    except Exception as e:
        logger.warning("Orthanc lookup error: %s", e)
    return None


def get_orthanc_study_main_uid(orthanc_study_id: str) -> Optional[str]:
    """
    Given an Orthanc internal study UUID, return the StudyInstanceUID
    that Orthanc actually has stored in MainDicomTags. This is the
    canonical UID OHIF/QIDO-RS must use.
    """
    try:
        r = _session().get(f"{ORTHANC_URL}/studies/{orthanc_study_id}", timeout=10)
        if r.status_code == 200:
            data = r.json() or {}
            return (data.get("MainDicomTags") or {}).get("StudyInstanceUID")
    except Exception as e:
        logger.warning("Orthanc main UID lookup error: %s", e)
    return None


def get_wado_study_url(orthanc_study_id: str) -> str:
    """Return the DICOMweb WADO-RS URL for a study — used by OHIF Viewer."""
    return f"{ORTHANC_URL}/dicom-web/studies/{orthanc_study_id}"


def delete_instance(orthanc_instance_id: str) -> bool:
    """Delete an instance from Orthanc (optional cleanup)."""
    try:
        r = _session().delete(f"{ORTHANC_URL}/instances/{orthanc_instance_id}", timeout=10)
        return r.status_code == 200
    except Exception:
        return False
