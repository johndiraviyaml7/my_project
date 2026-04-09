"""
QuantixMed — DICOM De-identifier
Standard: DICOM PS3.15 Annex E (2026) + HIPAA §164.514 Safe Harbor + FDA 21 CFR Part 11
"""
import hashlib
import logging
import uuid
from datetime import date, timedelta
from pathlib import Path
from typing import Optional

import pydicom
from pydicom.dataset import Dataset
from pydicom.sequence import Sequence
from pydicom.uid import generate_uid

from config.settings import DEID_ACTION_TABLE, COMPLIANCE, DEID_DICOM_DIR

logger = logging.getLogger(__name__)


class DicomDeidentifier:
    """
    Implements DICOM PS3.15 Annex E de-identification profile.
    Supports: Basic Application Confidentiality Profile
              Clean Pixel Data Option (burn-in text masking placeholder)
              Retain Longitudinal Temporal Information option (date offset)
    """

    # Stable date-offset seed per patient (consistent pseudo-dates across study)
    DATE_OFFSET_DAYS = -1000   # shift all dates back by ~2.7 years

    def __init__(self, subject_id: str, output_root: Optional[Path] = None):
        self.subject_id = subject_id
        self.output_root = output_root or DEID_DICOM_DIR
        # Deterministic surrogate patient ID derived from original (no reverse mapping)
        self.surrogate_patient_id = self._make_surrogate_id(subject_id)
        self._uid_map: dict[str, str] = {}   # original UID → surrogate UID
        self.audit_log: list[dict] = []

    # ── Helpers ──────────────────────────────────────────────────────────

    @staticmethod
    def _make_surrogate_id(original: str) -> str:
        """One-way hash → surrogate patient ID (no reversibility)."""
        h = hashlib.sha256(original.encode()).hexdigest()[:16].upper()
        return f"ANON-{h}"

    def _get_surrogate_uid(self, original_uid: str) -> str:
        """Map original DICOM UID to a stable generated surrogate UID."""
        if original_uid not in self._uid_map:
            # Deterministic: same input always produces same surrogate in this run
            seed = hashlib.md5(original_uid.encode()).hexdigest()
            new_uid = f"2.25.{int(seed, 16) % (10**30)}"
            self._uid_map[original_uid] = new_uid
        return self._uid_map[original_uid]

    def _offset_date(self, da_value: str) -> str:
        """Offset a DICOM DA-format date string by DATE_OFFSET_DAYS."""
        if not da_value or len(da_value) < 8:
            return ""
        try:
            d = date(int(da_value[:4]), int(da_value[4:6]), int(da_value[6:8]))
            d2 = d + timedelta(days=self.DATE_OFFSET_DAYS)
            return d2.strftime("%Y%m%d")
        except ValueError:
            return ""

    def _log_action(self, tag: str, keyword: str, action: str) -> None:
        self.audit_log.append({
            "tag": tag,
            "keyword": keyword,
            "action": action,
            "standard": COMPLIANCE["standard"],
            "hipaa_ref": COMPLIANCE["hipaa"],
        })

    # ── Core De-identification ────────────────────────────────────────────

    def _process_element(self, elem, ds: Dataset) -> bool:
        """
        Process a single DICOM element according to the action table.
        Returns True if element should be deleted.
        """
        tag_key = f"({elem.tag.group:04X},{elem.tag.element:04X})"
        action = DEID_ACTION_TABLE.get(tag_key)

        if action is None:
            # Not in table → apply basic profile default:
            # Private tags → remove; Group 18/28/50/60 image descriptors → keep
            if elem.tag.is_private:
                self._log_action(tag_key, getattr(elem, "keyword", "Private"), "X-private")
                return True
            return False  # keep by default

        if action == "K":
            return False  # Keep unchanged

        elif action == "X":
            self._log_action(tag_key, elem.keyword, "REMOVED")
            return True

        elif action == "Z":
            # Replace with zero-length or anonymised value
            if elem.VR in ("PN",):
                elem.value = "ANONYMOUS"
            elif elem.VR in ("LO", "SH", "ST", "LT", "UT", "CS"):
                elem.value = ""
            elif elem.VR == "DA":
                elem.value = ""
            elif elem.VR == "DS":
                elem.value = "0"
            elif elem.VR in ("US", "SS", "IS"):
                elem.value = 0
            else:
                elem.value = ""
            self._log_action(tag_key, elem.keyword, "REPLACED-ZERO")
            return False

        elif action == "D":
            # Replace with dummy / offset date
            if elem.VR == "DA":
                elem.value = self._offset_date(str(elem.value))
            elif elem.VR == "TM":
                elem.value = "000000.000"
            elif elem.VR == "DT":
                elem.value = ""
            else:
                elem.value = "ANONYMISED"
            self._log_action(tag_key, elem.keyword, "REPLACED-DUMMY")
            return False

        elif action == "U":
            # Replace UID with surrogate
            if elem.VR == "UI":
                elem.value = self._get_surrogate_uid(str(elem.value))
            self._log_action(tag_key, elem.keyword, "UID-REPLACED")
            return False

        elif action == "C":
            # Clean — for text fields, replace with cleaned version
            if elem.VR in ("ST", "LT", "UT"):
                elem.value = "[CLEANED]"
            self._log_action(tag_key, elem.keyword, "CLEANED")
            return False

        return False

    def deidentify_dataset(self, ds: Dataset) -> Dataset:
        """Apply full de-identification profile to a pydicom Dataset."""
        tags_to_delete = []

        for elem in ds:
            if elem.VR == "SQ":
                # Recurse into sequences
                for seq_item in elem.value:
                    self.deidentify_dataset(seq_item)
            else:
                if self._process_element(elem, ds):
                    tags_to_delete.append(elem.tag)

        for tag in tags_to_delete:
            del ds[tag]

        # Inject de-identification markers (DICOM PS3.15 Annex E §E.1)
        ds.PatientIdentityRemoved = "YES"
        # DeidentificationMethod is VR=LO, max 64 chars — keep it short.
        ds.DeidentificationMethod = "DCM PS3.15 AnnexE Basic; HIPAA SafeHarbor"
        # Surrogate patient ID
        ds.PatientID = self.surrogate_patient_id
        ds.PatientName = "ANONYMOUS"

        return ds

    def deidentify_file(self, input_path: Path, output_dir: Optional[Path] = None) -> Path:
        """De-identify a DICOM file and write output. Returns output path."""
        out_dir = output_dir or (self.output_root / self.surrogate_patient_id)
        out_dir.mkdir(parents=True, exist_ok=True)

        ds = pydicom.dcmread(str(input_path))
        self.deidentify_dataset(ds)

        out_file = out_dir / input_path.name
        ds.save_as(str(out_file))
        logger.debug("De-identified: %s → %s", input_path.name, out_file)
        return out_file
