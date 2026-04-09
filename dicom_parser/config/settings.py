"""
QuantixMed DICOM Parser — Configuration
JPEG/collage functionality removed. Orthanc WADO-RS integration added.
"""
import os
from pathlib import Path

# ── Database ─────────────────────────────────────────────────────
DB_HOST     = os.getenv("DB_HOST",     "localhost")
DB_PORT     = int(os.getenv("DB_PORT", "5432"))
DB_NAME     = os.getenv("DB_NAME",     "quantixmed_dicom")
DB_USER     = os.getenv("DB_USER",     "postgres")
DB_PASSWORD = os.getenv("DB_PASSWORD", "postgres")
DATABASE_URL = f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"

# ── Paths ────────────────────────────────────────────────────────
BASE_DIR       = Path(__file__).resolve().parent.parent
DATA_DIR       = BASE_DIR / "data"
OUTPUT_DIR     = BASE_DIR / "output"
DEID_DICOM_DIR = OUTPUT_DIR / "deid_dicom"
LOGS_DIR       = BASE_DIR / "logs"

# Root containing one or more <subject>/<study>/<series>/*.dcm trees.
# In Docker this is bind-mounted from the host (e.g. ./PSMA_one_dicom).
INPUT_DATA_DIR = Path(os.getenv("INPUT_DATA_DIR", str(BASE_DIR / "input")))

for d in [DATA_DIR, OUTPUT_DIR, DEID_DICOM_DIR, LOGS_DIR]:
    d.mkdir(parents=True, exist_ok=True)

# ── Orthanc ──────────────────────────────────────────────────────
ORTHANC_URL      = os.getenv("ORTHANC_URL",      "http://localhost:8042")
ORTHANC_USER     = os.getenv("ORTHANC_USER",     "admin")
ORTHANC_PASSWORD = os.getenv("ORTHANC_PASSWORD", "admin")
# Whether to upload de-identified DICOMs to Orthanc automatically
ORTHANC_AUTO_UPLOAD = os.getenv("ORTHANC_AUTO_UPLOAD", "true").lower() == "true"

# ── De-identification (DICOM PS3.15 Annex E + HIPAA §164.514) ────
DEID_ACTION_TABLE = {
    "(0010,0010)": "Z",   # PatientName
    "(0010,0020)": "Z",   # PatientID
    "(0010,0030)": "X",   # PatientBirthDate
    "(0010,0032)": "X",   # PatientBirthTime
    "(0010,0040)": "K",   # PatientSex
    "(0010,1000)": "X",   # OtherPatientIDs
    "(0010,1001)": "X",   # OtherPatientNames
    "(0010,1010)": "K",   # PatientAge
    "(0010,1020)": "K",   # PatientSize
    "(0010,1030)": "K",   # PatientWeight
    "(0010,1040)": "X",   # PatientAddress
    "(0010,2154)": "X",   # PatientTelephoneNumbers
    "(0010,2160)": "X",   # EthnicGroup
    "(0010,21B0)": "X",   # AdditionalPatientHistory
    "(0010,4000)": "X",   # PatientComments
    "(0008,0020)": "D",   # StudyDate
    "(0008,0030)": "X",   # StudyTime
    "(0008,0050)": "Z",   # AccessionNumber
    "(0008,0080)": "X",   # InstitutionName
    "(0008,0081)": "X",   # InstitutionAddress
    "(0008,0090)": "Z",   # ReferringPhysicianName
    "(0008,0096)": "X",   # ReferringPhysicianIdentificationSequence
    "(0008,1030)": "K",   # StudyDescription
    "(0008,1048)": "X",   # PhysicianOfRecord
    "(0008,1050)": "X",   # PerformingPhysicianName
    "(0008,1070)": "X",   # OperatorsName
    "(0032,1032)": "X",   # RequestingPhysician
    "(0020,000D)": "U",   # StudyInstanceUID
    "(0020,000E)": "U",   # SeriesInstanceUID
    "(0008,0018)": "U",   # SOPInstanceUID
    "(0020,0052)": "U",   # FrameOfReferenceUID
    "(0008,0021)": "D",   # SeriesDate
    "(0008,0022)": "D",   # AcquisitionDate
    "(0008,0023)": "D",   # ContentDate
    "(0008,1010)": "X",   # StationName
    "(0018,1000)": "X",   # DeviceSerialNumber
    "(0018,1030)": "K",   # ProtocolName
    "(0008,0060)": "K",   # Modality
    "(0008,0070)": "K",   # Manufacturer
    "(0008,1090)": "K",   # ManufacturerModelName
    "(0018,0015)": "K",   # BodyPartExamined
    "(0018,0050)": "K",   # SliceThickness
    "(0018,5100)": "K",   # PatientPosition
    "(0028,0010)": "K",   # Rows
    "(0028,0011)": "K",   # Columns
    "(0028,0030)": "K",   # PixelSpacing
    "(0028,0100)": "K",   # BitsAllocated
    "(0028,0101)": "K",   # BitsStored
    "(0028,1050)": "K",   # WindowCenter
    "(0028,1051)": "K",   # WindowWidth
}

COMPLIANCE = {
    "standard":    "DICOM PS3.15 2026 AnnexE",
    "hipaa":       "45 CFR §164.514(b) Safe Harbor",
    "fda":         "21 CFR Part 11",
    "ihe_profile": "IHE ITI-78 De-Identification",
}
