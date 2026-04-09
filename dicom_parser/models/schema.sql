-- ============================================================
-- QuantixMed DICOM Database Schema — v2 (JPEG-free, Orthanc-integrated)
-- DICOM 2026 | HIPAA §164.514 | FDA 21 CFR Part 11
-- Viewing handled by Orthanc (WADO-RS) + OHIF Viewer
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── PACS Devices ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pacs_devices (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    serial_number   VARCHAR(100) UNIQUE,
    modality        VARCHAR(50),
    status          VARCHAR(50) DEFAULT 'Disconnected',
    location        VARCHAR(255),
    manufacturer    VARCHAR(255),
    model           VARCHAR(255),
    ip_address      VARCHAR(50),
    port            INTEGER DEFAULT 11112,
    ae_title        VARCHAR(64),
    description     TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ── Subjects ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subjects (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id               UUID REFERENCES pacs_devices(id) ON DELETE SET NULL,
    subject_id              VARCHAR(255) NOT NULL UNIQUE,
    collection              VARCHAR(255),
    site                    VARCHAR(255),
    species_code            VARCHAR(50),
    species_description     VARCHAR(255),
    is_phantom              BOOLEAN DEFAULT FALSE,
    zip_file_path           TEXT,
    data_root_path          TEXT,
    load_status             VARCHAR(50) DEFAULT 'Pending',
    total_studies           INTEGER DEFAULT 0,
    total_series            INTEGER DEFAULT 0,
    total_instances         INTEGER DEFAULT 0,
    -- De-identified fields
    deid_subject_id         VARCHAR(255),
    deid_method             TEXT,
    deid_date               TIMESTAMPTZ,
    -- PHI masked columns (HIPAA §164.514 — 18 identifiers)
    phi_patient_name        BYTEA,
    phi_patient_id          BYTEA,
    phi_birth_date          BYTEA,
    phi_ethnic_group        BYTEA,
    metadata_json           JSONB,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

-- ── Patients ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS patients (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_id              UUID REFERENCES subjects(id) ON DELETE CASCADE,
    dicom_patient_id        VARCHAR(255),
    patient_sex             VARCHAR(10),
    patient_age             VARCHAR(20),
    patient_weight          DECIMAL(10,2),
    patient_size            DECIMAL(10,2),
    ethnic_group            VARCHAR(100),
    patient_identity_removed VARCHAR(10),
    deidentification_method TEXT,
    phi_patient_name        BYTEA,
    phi_birth_date          BYTEA,
    phi_address             BYTEA,
    deid_patient_id         VARCHAR(255),
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

-- ── Studies ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS studies (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subject_id                  UUID REFERENCES subjects(id) ON DELETE CASCADE,
    study_instance_uid          VARCHAR(255) UNIQUE NOT NULL,
    study_date                  DATE,
    study_time                  TIME,
    study_description           TEXT,
    study_id                    VARCHAR(64),
    accession_number            VARCHAR(64),
    referring_physician_name    TEXT,
    admitting_diagnosis         TEXT,
    total_series                INTEGER DEFAULT 0,
    total_instances             INTEGER DEFAULT 0,
    -- Orthanc integration
    orthanc_study_id            VARCHAR(255),       -- Orthanc internal UUID
    orthanc_study_uid           VARCHAR(255),       -- de-identified StudyInstanceUID in Orthanc
    wado_study_url              TEXT,               -- DICOMweb WADO-RS base URL for OHIF
    -- De-identified fields
    deid_study_instance_uid     VARCHAR(255),
    deid_study_date             DATE,
    deid_accession_number       VARCHAR(64),
    phi_referring_physician     BYTEA,
    metadata_json               JSONB,
    created_at                  TIMESTAMPTZ DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ DEFAULT NOW()
);

-- ── Series ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS series (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    study_id                UUID REFERENCES studies(id) ON DELETE CASCADE,
    series_instance_uid     VARCHAR(255) UNIQUE NOT NULL,
    series_number           INTEGER,
    series_date             DATE,
    series_time             TIME,
    series_description      TEXT,
    modality                VARCHAR(50),
    body_part_examined      VARCHAR(100),
    protocol_name           VARCHAR(255),
    manufacturer            VARCHAR(255),
    manufacturer_model      VARCHAR(255),
    software_versions       VARCHAR(255),
    pixel_spacing_row       DECIMAL(10,6),
    pixel_spacing_col       DECIMAL(10,6),
    slice_thickness         DECIMAL(10,4),
    patient_position        VARCHAR(50),
    image_count             INTEGER DEFAULT 0,
    -- Orthanc integration
    orthanc_series_id       VARCHAR(255),       -- Orthanc internal UUID for this series
    deid_series_instance_uid VARCHAR(255),
    deid_series_date        DATE,
    metadata_json           JSONB,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

-- ── Instances ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS instances (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    series_id               UUID REFERENCES series(id) ON DELETE CASCADE,
    sop_instance_uid        VARCHAR(255) UNIQUE NOT NULL,
    sop_class_uid           VARCHAR(255),
    instance_number         INTEGER,
    acquisition_number      INTEGER,
    acquisition_date        DATE,
    acquisition_time        TIME,
    content_date            DATE,
    content_time            TIME,
    rows                    INTEGER,
    cols                    INTEGER,
    bits_allocated          INTEGER,
    bits_stored             INTEGER,
    samples_per_pixel       INTEGER,
    photometric_interp      VARCHAR(50),
    slice_location          DECIMAL(15,6),
    image_position_patient  DECIMAL(15,6)[],
    image_orientation       DECIMAL(10,6)[],
    pixel_spacing           DECIMAL(10,6)[],
    file_path               TEXT,                -- original DICOM file path
    file_size_bytes         BIGINT,
    -- Orthanc integration
    orthanc_instance_id     VARCHAR(255),        -- Orthanc internal UUID for this instance
    -- De-identified
    deid_sop_instance_uid   VARCHAR(255),
    deid_file_path          TEXT,
    metadata_json           JSONB,
    created_at              TIMESTAMPTZ DEFAULT NOW(),
    updated_at              TIMESTAMPTZ DEFAULT NOW()
);

-- ── De-identification Audit Log ───────────────────────────────
CREATE TABLE IF NOT EXISTS deid_audit_log (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type             VARCHAR(50),
    entity_id               UUID,
    original_tag            VARCHAR(20),
    original_keyword        VARCHAR(255),
    action                  VARCHAR(50),
    standard_reference      VARCHAR(100),
    processed_at            TIMESTAMPTZ DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_subjects_device_id ON subjects(device_id);
CREATE INDEX IF NOT EXISTS idx_subjects_subject_id ON subjects(subject_id);
CREATE INDEX IF NOT EXISTS idx_patients_subject_id ON patients(subject_id);
CREATE INDEX IF NOT EXISTS idx_studies_subject_id ON studies(subject_id);
CREATE INDEX IF NOT EXISTS idx_studies_study_uid ON studies(study_instance_uid);
CREATE INDEX IF NOT EXISTS idx_studies_orthanc_id ON studies(orthanc_study_id);
CREATE INDEX IF NOT EXISTS idx_series_study_id ON series(study_id);
CREATE INDEX IF NOT EXISTS idx_series_uid ON series(series_instance_uid);
CREATE INDEX IF NOT EXISTS idx_series_orthanc_id ON series(orthanc_series_id);
CREATE INDEX IF NOT EXISTS idx_instances_series_id ON instances(series_id);
CREATE INDEX IF NOT EXISTS idx_instances_sop_uid ON instances(sop_instance_uid);
CREATE INDEX IF NOT EXISTS idx_instances_orthanc_id ON instances(orthanc_instance_id);
CREATE INDEX IF NOT EXISTS idx_deid_audit_entity ON deid_audit_log(entity_type, entity_id);

-- ============================================================
-- v5 delta — PAS / Edge Connector connectivity tracking
-- ============================================================

ALTER TABLE pacs_devices
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS pacs_status_events (
    id             BIGSERIAL PRIMARY KEY,
    device_id      UUID REFERENCES pacs_devices(id) ON DELETE CASCADE,
    serial_number  VARCHAR(100),
    event_type     VARCHAR(32) NOT NULL,
    topic          VARCHAR(255),
    payload        TEXT,
    occurred_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_status_events_device_id   ON pacs_status_events(device_id);
CREATE INDEX IF NOT EXISTS idx_status_events_occurred_at ON pacs_status_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_status_events_serial      ON pacs_status_events(serial_number);
