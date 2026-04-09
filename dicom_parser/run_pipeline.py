#!/usr/bin/env python3
"""
QuantixMed DICOM Parser — CLI Runner
Usage: python run_pipeline.py --psma-dir ./psma_data --device-name "PSMA-PET-CT PACS"

Windows note: Run from inside the dicom_parser directory, or set PYTHONPATH:
    cd dicom_parser
    python run_pipeline.py --psma-dir ..\psma_data
"""
import argparse
import logging
import os
import sys
from pathlib import Path

# Ensure local packages are importable regardless of CWD (important on Windows)
_SCRIPT_DIR = Path(__file__).resolve().parent
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

# Check critical dependencies before importing anything else
_missing = []
for _pkg, _import in [("pydicom", "pydicom"), ("psycopg2-binary", "psycopg2"),
                       ("fastapi", "fastapi"), ("requests", "requests")]:
    try:
        __import__(_import)
    except ImportError:
        _missing.append(_pkg)

if _missing:
    print(f"\n❌  Missing required packages: {', '.join(_missing)}")
    print("   Run:  python -m pip install --upgrade pip")
    print("         pip install -r requirements.txt\n")
    sys.exit(1)

from config.settings import DATA_DIR, INPUT_DATA_DIR
from models.database import init_pool, init_schema
from parsers.dicom_parser import (
    register_pacs_device,
    load_subjects_csv,
    process_subject,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler("logs/pipeline.log"),
    ],
)
logger = logging.getLogger("run_pipeline")


def main():
    parser = argparse.ArgumentParser(description="QuantixMed DICOM Pipeline")
    parser.add_argument("--psma-dir",    type=Path, default=INPUT_DATA_DIR)
    parser.add_argument("--csv-file",    type=Path, default=None)
    parser.add_argument("--device-name", type=str, default="PSMA-PET-CT PACS")
    parser.add_argument("--serial",      type=str, default="PSMA-PET-2026-001")
    parser.add_argument("--modality",    type=str, default="PT")
    parser.add_argument("--manufacturer",type=str, default="Siemens Healthineers")
    parser.add_argument("--model",       type=str, default="Biograph mCT Flow")
    parser.add_argument("--ip",          type=str, default="192.168.1.200")
    parser.add_argument("--port",        type=int, default=11112)
    parser.add_argument("--default-collection", type=str, default="TCIA PSMA-PET-CT-Lesions",
                        help="Used when DICOM has no ClinicalTrialProtocolName")
    parser.add_argument("--default-site",       type=str, default="Unknown",
                        help="Used when DICOM has no InstitutionName")
    parser.add_argument("--default-species",    type=str, default="Homo sapiens",
                        help="Used when DICOM has no PatientSpeciesDescription")
    args = parser.parse_args()

    # 1. Init DB
    logger.info("Initialising database connection…")
    init_pool()
    init_schema()

    # 2. Register PACS Device
    logger.info("Registering PACS device: %s", args.device_name)
    device_id = register_pacs_device(
        name=args.device_name,
        serial_number=args.serial,
        modality=args.modality,
        status="Active",
        location="Nuclear Medicine – Floor 3",
        manufacturer=args.manufacturer,
        model=args.model,
        ip_address=args.ip,
        port=args.port,
        ae_title="QXMED_PACS",
        description="PSMA-PET-CT-Lesions collection — TCIA",
    )
    logger.info("Device ID: %s", device_id)

    # 3. Load CSV metadata
    psma_dir = args.psma_dir
    csv_path = args.csv_file or (psma_dir / "subjects_metadata_dicom.csv")
    csv_meta = {}
    if csv_path.exists():
        csv_meta = load_subjects_csv(csv_path)
        logger.info("CSV loaded: %d subjects", len(csv_meta))
    else:
        logger.warning("CSV not found at %s — continuing without metadata", csv_path)

    # 4. Process each subject directory
    #    A subject directory is any non-hidden subdirectory of psma_dir.
    subject_dirs = [d for d in sorted(psma_dir.iterdir())
                    if d.is_dir() and not d.name.startswith(".")]

    if not subject_dirs:
        logger.error("No subject directories found in %s", psma_dir)
        sys.exit(1)

    logger.info("Found %d subject directories", len(subject_dirs))
    total_stats = {"studies": 0, "series": 0, "instances": 0}

    for subject_dir in subject_dirs:
        meta = csv_meta.get(subject_dir.name)
        try:
            result = process_subject(
                device_id, subject_dir, meta,
                default_collection=args.default_collection,
                default_site=args.default_site,
                default_species=args.default_species,
            )
            for k in total_stats:
                total_stats[k] += result.get(k, 0)
        except Exception as e:
            logger.error("Failed processing %s: %s", subject_dir.name, e, exc_info=True)

    logger.info(
        "Pipeline complete — %d studies | %d series | %d instances",
        total_stats["studies"], total_stats["series"], total_stats["instances"]
    )


if __name__ == "__main__":
    main()
