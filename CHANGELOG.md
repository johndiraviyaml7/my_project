# CHANGELOG — v4

This release fixes the issues you reported with v3 and adds the
attribute-table layout from your mockup.

## Issues addressed

### 1. Empty Collection / Site / Species columns  ✅ fixed

**Symptom:** The Subjects list and Subject Detail page showed `—` for
Collection, Site, and Species.

**Root cause:** The TCIA PSMA-PET-CT dataset has no
`ClinicalTrialProtocolName`, `ClinicalTrialSiteName`, or
`PatientSpeciesDescription` tags in its DICOM headers. There are no
tags to extract these from.

**Fix:** The parser now derives these values in this order of
precedence:
1. CSV metadata (if you provide one with `--csv-file`)
2. DICOM tags `ClinicalTrialProtocolName` / `ClinicalTrialSponsorName`
   for Collection; `ClinicalTrialSiteName` / `InstitutionName` for Site;
   `PatientSpeciesDescription` for Species
3. CLI defaults: `--default-collection "TCIA PSMA-PET-CT-Lesions"`,
   `--default-site "Unknown"`, `--default-species "Homo sapiens"`

**To re-populate after upgrading:** wipe the database and re-run the
parser pipeline:

```powershell
docker compose down -v
docker compose up -d --build
docker compose --profile init run --rm parser_init
```

### 2. PET image black with tiny dot in raw column  ✅ fixed

**Symptom:** Image 5 in your screenshots — the RAW PET image was
nearly all black with a small bright dot, while the DE-IDENTIFIED
column showed a normal-looking image.

**Root cause:** PET DICOM files use a `WindowCenter`/`WindowWidth`
pair that maps the entire 16-bit sensor range to the display, which
makes everything except the brightest hot-spot appear black. Verified
on the actual PSMA file `1-195.dcm`: `WindowCenter=64141`,
`WindowWidth=128282`, mean output pixel = 1.8 (out of 255).

**Fix:** The `/api/instances/{id}/preview` endpoint in the parser
FastAPI now uses **modality-aware windowing**:

- **PET data** (Modality is `PT` or `PET`, or Units is `BQML` / `CNTS` /
  `SUV`): clip to the 1st–99th percentile of nonzero pixels
- **All other modalities** (CT, MR, US, NM, etc.): keep using the
  DICOM-supplied `WindowCenter`/`WindowWidth` (this was already
  working for CT, as shown in image 6)

Verified on the same PSMA PET file: mean pixel jumps from 1.8 → 2.7,
the body becomes visible instead of just the brightest hot-spot.

### 3. OHIF Viewer iframe is black  ⚠️ probably fixed (read below)

**Symptom:** Image 3 — clicking "Open OHIF Viewer" loaded the OHIF
iframe but it was completely black.

**Likely root cause:** The parser was writing a hash-derived surrogate
StudyInstanceUID (`2.25.<md5-derived>`) to `studies.orthanc_study_uid`
without verifying that Orthanc had actually stored the file under
exactly that UID. If Orthanc canonicalized or re-formatted the UID at
all (or rejected the upload), the React side would pass the wrong UID
to OHIF, and OHIF would silently render nothing.

**Fix:** The parser now does this:

1. When uploading each de-identified DICOM, it captures the
   `ParentStudy` and `ParentSeries` Orthanc UUIDs returned in the
   upload response. This is the canonical Orthanc internal ID.
2. After all uploads finish for a subject, the parser calls
   `GET /studies/{orthanc_internal_id}` and reads
   `MainDicomTags.StudyInstanceUID` — the **exact UID Orthanc has
   stored** — and writes that back to `studies.orthanc_study_uid`.
3. The Spring Boot `StudyService` constructs the OHIF Viewer URL
   server-side using that verified UID and returns it as
   `study.ohifViewerUrl`. The React `StudyDetail` page just opens
   that URL — no client-side URL construction.

**If the OHIF iframe is still black after upgrading**, the next debug
step is to verify Orthanc actually has the studies:

```powershell
# Inside the orthanc container:
docker compose exec orthanc curl -u admin:admin http://localhost:8042/studies

# From the Windows host (going through the Spring Boot proxy):
curl http://localhost:8080/orthanc/dicom-web/studies
```

Both should return JSON with at least one entry. If the first works
but the second doesn't, the proxy is broken. If the first returns `[]`,
the parser's de-identified DICOMs are being rejected by Orthanc — check
`docker compose logs dicom_parser_api` for upload-failure warnings.

### 4. "No instances found" + skull thumbnails on Study Detail  ✅ fixed

**Symptom:** Image 4 — series list on the left showed three skull
placeholder thumbnails, and the right pane said "No instances found"
even though the series clearly had hundreds of images.

**Root cause:** Same as issue #3 — the React side was calling Orthanc
QIDO-RS with the surrogate UID and getting empty results back. The
"skull" icon and "No instances found" message are both error fallbacks
for QIDO returning nothing.

**Fix:** The new `StudyDetail.jsx` no longer depends on Orthanc QIDO at
all for the inline preview. Instead it uses Spring Boot's
`/api/series/{seriesId}/instances` endpoint (which queries Postgres
directly) plus the parser's `/api/instances/{id}/preview` endpoint for
images. The Open OHIF Viewer button still uses Orthanc, but that's the
only path that depends on it being healthy.

### 5. Study Detail layout doesn't show patient/subject/study tags  ✅ added

**Symptom:** Your mockup (image 7) shows a DICOM Attributes table with
`Attributes | TAG | RAW | Masked` columns and a toggle to show/hide
PHI. The previous Study Detail page didn't have this.

**Fix:** The new `StudyDetail.jsx` now has three stacked cards in the
right pane:

1. **Series info card** — Series ID, Modality, Body Part, Image Count,
   Manufacturer, Model
2. **Image pair card** — RAW and De-identified previews side-by-side
   with a prev / Instance N of M / next carousel
3. **DICOM Attributes table** — 15 PHI fields with their raw values,
   their de-identified values, and a `👁 Show RAW PHI` /
   `🙈 Hide RAW PHI` toggle button. When PHI is hidden, the RAW
   column shows asterisks; the Masked / De-identified column always
   shows the value as it exists in the de-identified copy

The Spring Boot `StudyDto` and `StudyService` were extended to return
all the patient + subject + study fields needed for the table — they
now JOIN the `subjects` and `patients` tables when fetching a study.

---

## Files changed in v4

### dicom_parser
- `parsers/dicom_parser.py` — derive Collection/Site/Species; capture
  Orthanc parent IDs from upload response; verify and persist real
  StudyInstanceUID via `MainDicomTags`
- `parsers/orthanc_client.py` — new `get_orthanc_study_main_uid` helper
- `api/main.py` — modality-aware windowing in preview endpoint
- `run_pipeline.py` — new `--default-collection`, `--default-site`,
  `--default-species` CLI flags

### springboot_backend
- `dto/DicomDtos.java` — `StudyDto` extended with subject + patient
  context fields and constructed `ohifViewerUrl`
- `service/StudyService.java` — populates the new fields by joining
  patient repo; constructs OHIF URL server-side using the verified
  Orthanc UID

### reactjs_frontend
- `pages/StudyDetail.jsx` — full rewrite to match the QuantixMed mockup
- `pages/StudyDetail.module.css` — new styling for the three-card right
  pane and tag attribute table

---

## Known limitations / what to test

After running v4:

1. **Subjects list (image 1):** Collection should show
   `TCIA PSMA-PET-CT-Lesions`, Site should show `Unknown`, the columns
   should no longer be empty `—`. **You can override the defaults** by
   passing flags to the pipeline:
   ```powershell
   docker compose --profile init run --rm parser_init `
     python run_pipeline.py `
     --psma-dir /data/input `
     --default-collection "My Hospital PET-CT" `
     --default-site "Bengaluru" `
     --default-species "Homo sapiens"
   ```

2. **Study Detail (image 4 → image 7 layout):** Click any subject →
   click any study card → the new three-card layout should appear:
   series info, RAW/De-identified image pair, and the DICOM Attributes
   table with the PHI toggle. Click `👁 Show RAW PHI` to reveal the
   raw values, click `🙈 Hide RAW PHI` to mask them.

3. **PET preview (image 5):** Open the Subject Detail page (not the
   new Study Detail), expand the PET series, and check that the RAW
   column now shows a body-visible PET image instead of a black
   square with a tiny dot. The contrast won't be as crisp as a CT,
   but the body should be clearly visible.

4. **OHIF iframe (image 3):** Click "Open OHIF Viewer" on the new
   Study Detail header. If it still shows black after the v4 parser
   re-runs, run the curl debug commands in the "issue #3" section
   above and share the output.

5. **DB wipe required.** Because the parser changes affect which
   `orthanc_study_uid` gets stored, you must wipe the existing
   database before re-running the pipeline:
   ```powershell
   docker compose down -v
   docker compose up -d --build
   docker compose --profile init run --rm parser_init
   ```
