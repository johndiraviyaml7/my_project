/**
 * OrthancService — Direct DICOMweb client via Spring Boot proxy.
 * 
 * All requests go to Spring Boot /orthanc/** which proxies to Orthanc
 * with Basic Auth injected server-side. No CORS issues, no credentials
 * exposed to the browser.
 * 
 * DICOMweb standard:
 *   QIDO-RS: Query studies/series/instances (JSON metadata)
 *   WADO-RS: Retrieve pixel data (rendered JPEG/PNG)
 */

const PROXY = 'http://localhost:8080/orthanc';
const DICOMWEB = `${PROXY}/dicom-web`;
const OHIF_URL = import.meta.env.VITE_OHIF_URL || 'http://localhost:3000';

// ── QIDO-RS: Query ──────────────────────────────────────────────────────────

/** Get all studies from Orthanc (QIDO-RS) */
export async function qidoStudies(params = {}) {
  const q = new URLSearchParams({
    includefield: 'all',
    limit: '200',
    ...params,
  });
  const res = await fetch(`${DICOMWEB}/studies?${q}`, {
    headers: { Accept: 'application/json' },
  });
  if (!res.ok) throw new Error(`QIDO studies failed: ${res.status}`);
  return res.json(); // array of DICOM JSON objects
}

/** Get all series for a study (QIDO-RS) */
export async function qidoSeries(studyInstanceUID) {
  const res = await fetch(
    `${DICOMWEB}/studies/${studyInstanceUID}/series?includefield=all`,
    { headers: { Accept: 'application/json' } }
  );
  if (!res.ok) throw new Error(`QIDO series failed: ${res.status}`);
  return res.json();
}

/** Get instances for a series (QIDO-RS) */
export async function qidoInstances(studyInstanceUID, seriesInstanceUID) {
  const res = await fetch(
    `${DICOMWEB}/studies/${studyInstanceUID}/series/${seriesInstanceUID}/instances?includefield=all`,
    { headers: { Accept: 'application/json' } }
  );
  if (!res.ok) throw new Error(`QIDO instances failed: ${res.status}`);
  return res.json();
}

/** Get first instance UID for a series (needed to build thumbnail URL) */
export async function getFirstInstanceUID(studyUID, seriesUID) {
  const instances = await qidoInstances(studyUID, seriesUID);
  if (!instances || instances.length === 0) return null;
  // DICOM tag 00080018 = SOPInstanceUID
  return instances[0]['00080018']?.Value?.[0] ?? null;
}

// ── WADO-RS: Image URLs ─────────────────────────────────────────────────────

/**
 * Returns a rendered JPEG URL for a specific instance via WADO-RS.
 * viewport param resizes the image server-side (Orthanc supports this).
 */
export function wadoRenderedUrl(studyUID, seriesUID, instanceUID, width = 200, height = 200) {
  return `${DICOMWEB}/studies/${studyUID}/series/${seriesUID}/instances/${instanceUID}/rendered?viewport=${width},${height}&quality=75`;
}

/**
 * Returns thumbnail URL for the first frame of a series.
 * Orthanc exposes: /wado?requestType=WADO&studyUID=...&seriesUID=...&objectUID=...&contentType=image/jpeg
 */
export function wadoThumbnailUrl(studyUID, seriesUID, instanceUID) {
  return `${PROXY}/wado?requestType=WADO&studyUID=${studyUID}&seriesUID=${seriesUID}&objectUID=${instanceUID}&contentType=image%2Fjpeg&rows=200&columns=200`;
}

// ── OHIF Viewer URLs ────────────────────────────────────────────────────────

/**
 * Build OHIF Viewer URL for a study.
 * OHIF fetches DICOM data from the wadoRsRoot (Spring Boot proxy).
 */
export function buildOhifUrl(studyInstanceUID) {
  const wadoRsRoot = `http://localhost:8080/orthanc/dicom-web`;
  return `${OHIF_URL}/viewer?StudyInstanceUIDs=${studyInstanceUID}&wadoRsRoot=${encodeURIComponent(wadoRsRoot)}`;
}

// ── DICOM JSON helpers ──────────────────────────────────────────────────────

/** Extract a string value from a DICOM JSON object by tag */
export function dicomVal(obj, tag) {
  return obj?.[tag]?.Value?.[0] ?? null;
}

/** Extract PN (PersonName) value */
export function dicomPN(obj, tag) {
  const v = obj?.[tag]?.Value?.[0];
  return v?.Alphabetic ?? v ?? null;
}

// DICOM tags we care about
export const TAGS = {
  StudyInstanceUID:        '0020000D',
  SeriesInstanceUID:       '0020000E',
  SOPInstanceUID:          '00080018',
  StudyDate:               '00080020',
  StudyTime:               '00080030',
  StudyDescription:        '00081030',
  SeriesDescription:       '0008103E',
  SeriesNumber:            '00200011',
  Modality:                '00080060',
  BodyPartExamined:        '00180015',
  NumberOfSeriesRelatedInstances: '00201209',
  NumberOfStudyRelatedSeries:     '00201206',
  NumberOfStudyRelatedInstances:  '00201208',
  PatientID:               '00100020',
  PatientName:             '00100010',
  PatientSex:              '00100040',
  PatientAge:              '00101010',
};

// ── Orthanc REST API (not DICOMweb) ─────────────────────────────────────────

/** Check if Orthanc is reachable */
export async function checkOrthancStatus() {
  try {
    const res = await fetch(`${PROXY}/status`, {
      headers: { Accept: 'application/json' },
    });
    if (res.ok) return await res.json();
    return { available: false };
  } catch {
    return { available: false };
  }
}
