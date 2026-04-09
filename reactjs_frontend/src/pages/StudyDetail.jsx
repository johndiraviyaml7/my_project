/**
 * StudyDetail
 * ─────────────────────────────────────────────────────────────────
 * Layout (matches the QuantixMed mockup):
 *
 *   Breadcrumb
 *   Study header card  ──────────────────────────────────────────
 *     description, tags (date / series / instances / accession),
 *     Open OHIF Viewer button + Orthanc status indicator
 *
 *   Series strip (left)  │   Right pane:
 *     thumbnails of      │     ── Series info card ──
 *     each series        │     Series ID / Modality / Study ID / Series Time
 *                        │
 *                        │     ── Image pair card ──
 *                        │     RAW Dicom Image     |  De-identified Image
 *                        │     [<img>]             |  [<img>]
 *                        │     prev / instance N of N / next
 *                        │
 *                        │     ── Attribute table card ──
 *                        │     Attributes | TAG | RAW | Masked
 *                        │     PatientID  | 0010,0020 | PSMA_xxx | ****
 *                        │     PatientName| 0010,0010 | PSMA_xxx | ANONYMOUS
 *                        │     ...
 *                        │     [eye icon] toggle reveals Masked column
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api/apiService';
import OHIFViewer from '../components/OHIFViewer';
import styles from './StudyDetail.module.css';

const PARSER_URL = import.meta.env.VITE_PARSER_URL || 'http://localhost:8000';
const previewUrl = (instanceId, variant) =>
  `${PARSER_URL}/api/instances/${instanceId}/preview?variant=${variant}&max_size=512`;

const MODALITY_COLOR = {
  CT:  '#1d4ed8', PT: '#7c3aed', MR: '#059669',
  US:  '#d97706', NM: '#6366f1', SEG: '#dc2626',
};

// PHI tags shown in the attribute table.  `mask` is the value to display
// when the toggle is OFF (i.e. PHI hidden).
const PHI_TAG_DEFS = [
  { attr: 'Patient ID',         tag: '(0010,0020)', source: 'patientId',     deidSource: 'deidPatientId',   masker: () => '****' },
  { attr: 'Patient Name',       tag: '(0010,0010)', source: 'subjectLabel',  deidValue: 'ANONYMOUS' },
  { attr: 'Patient Birth Date', tag: '(0010,0030)', source: 'patientBirthDate', deidValue: '****' },
  { attr: 'Patient Sex',        tag: '(0010,0040)', source: 'patientSex',    keep: true },
  { attr: 'Patient Age',        tag: '(0010,1010)', source: 'patientAge',    keep: true },
  { attr: 'Patient Weight',     tag: '(0010,1030)', source: 'patientWeight', keep: true, suffix: ' kg' },
  { attr: 'Patient Size',       tag: '(0010,1020)', source: 'patientSize',   keep: true, suffix: ' m' },
  { attr: 'Phantom',            tag: '(0010,0024)', source: 'subjectIsPhantom', keep: true },
  { attr: 'Species Description',tag: '(0010,2202)', source: 'subjectSpecies',keep: true },
  { attr: 'Collection',         tag: '(0012,0060)', source: 'subjectCollection', keep: true },
  { attr: 'Site',               tag: '(0012,0031)', source: 'subjectSite',   keep: true },
  { attr: 'Study Instance UID', tag: '(0020,000D)', source: 'studyInstanceUid', deidSource: 'deidStudyInstanceUid' },
  { attr: 'Study Date',         tag: '(0008,0020)', source: 'studyDate',     deidSource: 'deidStudyDate' },
  { attr: 'Study Description',  tag: '(0008,1030)', source: 'studyDescription', keep: true },
  { attr: 'Accession Number',   tag: '(0008,0050)', source: 'accessionNumber', deidValue: '****' },
];

export default function StudyDetail() {
  const { studyId } = useParams();
  const navigate    = useNavigate();

  const [study,        setStudy]        = useState(null);
  const [seriesList,   setSeriesList]   = useState([]);
  const [selectedSeries, setSelectedSeries] = useState(null);
  const [instances,    setInstances]    = useState([]);
  const [carouselIdx,  setCarouselIdx]  = useState(0);
  const [showOhif,     setShowOhif]     = useState(false);
  const [showRaw,      setShowRaw]      = useState(false);  // PHI mask toggle: OFF = masked
  const [loading,      setLoading]      = useState(true);
  const [instLoading,  setInstLoading]  = useState(false);
  const [error,        setError]        = useState(null);

  // ── Initial load: study + series ───────────────────────────────────────
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([api.getStudy(studyId), api.getSeries(studyId)])
      .then(([st, sr]) => {
        if (cancelled) return;
        setStudy(st);
        const list = Array.isArray(sr) ? sr : [];
        setSeriesList(list);
        // Auto-select first series
        if (list.length > 0) {
          setSelectedSeries(list[0]);
        }
      })
      .catch(e => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [studyId]);

  // ── Load instances when selected series changes ────────────────────────
  useEffect(() => {
    if (!selectedSeries) return;
    let cancelled = false;
    setInstLoading(true);
    setInstances([]);
    setCarouselIdx(0);
    api.getInstances(selectedSeries.id)
      .then(list => {
        if (cancelled) return;
        const arr = Array.isArray(list) ? list : [];
        setInstances(arr);
        // Start at the middle slice (more interesting than slice 1)
        if (arr.length > 0) setCarouselIdx(Math.floor(arr.length / 2));
      })
      .catch(() => {})
      .finally(() => { if (!cancelled) setInstLoading(false); });
    return () => { cancelled = true; };
  }, [selectedSeries]);

  const stepCarousel = useCallback((delta) => {
    if (instances.length === 0) return;
    setCarouselIdx(i => (i + delta + instances.length) % instances.length);
  }, [instances.length]);

  if (loading) return <div className={styles.loading}>Loading study…</div>;
  if (error || !study) return <div className={styles.error}>Failed to load study: {error || 'not found'}</div>;

  const currentInstance = instances[carouselIdx];
  const canOpenOhif = study.hasViewer && study.ohifViewerUrl;

  return (
    <div className={styles.page}>
      {showOhif && (
        <OHIFViewer
          studyInstanceUid={study.orthancStudyUid || study.deidStudyInstanceUid}
          ohifViewerUrl={study.ohifViewerUrl}
          onClose={() => setShowOhif(false)}
        />
      )}

      {/* ── Breadcrumb ─────────────────────────────────────────── */}
      <div className={styles.breadcrumb}>
        <button onClick={() => navigate('/dicom')}>Devices</button>
        <span>›</span>
        <button onClick={() => navigate(`/dicom/subject/${study.subjectId}`)}>
          {study.subjectLabel || 'Subject'}
        </button>
        <span>›</span>
        <span className={styles.crumbCurrent}>Study · {study.studyDate || '—'}</span>
      </div>

      {/* ── Study header card ─────────────────────────────────── */}
      <div className="card" style={{padding:'18px 22px', marginBottom:14}}>
        <div className={styles.studyHead}>
          <div style={{flex:1, minWidth:0}}>
            <div className={styles.studyTitle}>{study.studyDescription || 'Untitled study'}</div>
            <div className={styles.studyTags}>
              <span className={styles.studyTag}>📅 {study.studyDate || '—'}</span>
              <span className={styles.studyTag}>🗂 {seriesList.length} series</span>
              <span className={styles.studyTag}>🖼 {study.totalInstances || 0} instances</span>
              {study.accessionNumber && (
                <span className={styles.studyTag}>🔢 {study.accessionNumber}</span>
              )}
            </div>
            <div className={styles.uidLine}>
              <span className={styles.uidLabel}>Study UID:</span>
              <code>{study.studyInstanceUid}</code>
            </div>
            {study.deidStudyInstanceUid && (
              <div className={styles.uidLine}>
                <span className={styles.uidLabel}>De-id UID:</span>
                <code style={{color:'#16a34a'}}>{study.deidStudyInstanceUid}</code>
              </div>
            )}
          </div>
          <div className={styles.headRight}>
            <button
              className={`${styles.ohifBtn} ${!canOpenOhif ? styles.ohifBtnDisabled : ''}`}
              onClick={() => canOpenOhif && setShowOhif(true)}
              disabled={!canOpenOhif}
              title={canOpenOhif ? 'Open in full OHIF Viewer' : 'Orthanc not connected for this study'}
            >
              🔬 Open OHIF Viewer
            </button>
            <div className={styles.orthancPill}>
              <span className={canOpenOhif ? styles.dotGreen : styles.dotRed} />
              {canOpenOhif ? 'Orthanc connected' : 'Orthanc unavailable'}
            </div>
          </div>
        </div>
      </div>

      {/* ── Main two-column layout ─────────────────────────────── */}
      <div className={styles.mainGrid}>

        {/* ── Series strip (left) ─────────────────────────────── */}
        <div className="card" style={{padding:'14px', overflow:'hidden'}}>
          <div className={styles.panelTitle}>SERIES ({seriesList.length})</div>
          <div className={styles.seriesScroll}>
            {seriesList.map((s, i) => {
              const isSel = selectedSeries?.id === s.id;
              const mc = MODALITY_COLOR[s.modality] || '#475569';
              return (
                <button key={s.id}
                        className={`${styles.seriesCard} ${isSel ? styles.seriesActive : ''}`}
                        onClick={() => setSelectedSeries(s)}>
                  <div className={styles.seriesNum}>#{s.seriesNumber || i+1}</div>
                  <div className={styles.seriesCardBody}>
                    <div className={styles.seriesDesc}>
                      {s.seriesDescription || 'Series'}
                    </div>
                    <div className={styles.seriesPills}>
                      <span className={styles.modalityPill}
                            style={{background: mc + '22', color: mc}}>
                        {s.modality || '—'}
                      </span>
                      <span className={styles.seriesImgCount}>
                        {s.imageCount ?? 0} images
                      </span>
                    </div>
                    {s.bodyPartExamined && (
                      <div className={styles.bodyPart}>{s.bodyPartExamined}</div>
                    )}
                  </div>
                </button>
              );
            })}
            {seriesList.length === 0 && (
              <div className={styles.emptyMsg}>No series for this study.</div>
            )}
          </div>
        </div>

        {/* ── Right pane ─────────────────────────────────────── */}
        <div className={styles.rightPane}>

          {/* Series info card */}
          {selectedSeries && (
            <div className="card" style={{padding:'14px 18px'}}>
              <div className={styles.seriesInfoGrid}>
                <Field label="Series ID"    value={`#${selectedSeries.seriesNumber || '?'}`} />
                <Field label="Modality"     value={selectedSeries.modality} />
                <Field label="Body Part"    value={selectedSeries.bodyPartExamined} />
                <Field label="Image Count"  value={selectedSeries.imageCount} />
                <Field label="Manufacturer" value={selectedSeries.manufacturer} />
                <Field label="Model"        value={selectedSeries.manufacturerModel} />
              </div>
            </div>
          )}

          {/* Image pair card */}
          {selectedSeries && (
            <div className="card" style={{padding:'14px 18px'}}>
              <div className={styles.imageCardHeader}>
                <div className={styles.imageCardTitle}>RAW vs De-identified Preview</div>
                {instances.length > 0 && (
                  <div className={styles.carouselNav}>
                    <button className={styles.navBtn}
                            onClick={() => stepCarousel(-1)}
                            disabled={instances.length < 2}>‹ Prev</button>
                    <span className={styles.carouselCounter}>
                      Instance {carouselIdx + 1} of {instances.length}
                      {currentInstance && <span className={styles.instNum}>
                        &nbsp;·&nbsp;#{currentInstance.instanceNumber}
                      </span>}
                    </span>
                    <button className={styles.navBtn}
                            onClick={() => stepCarousel(1)}
                            disabled={instances.length < 2}>Next ›</button>
                  </div>
                )}
              </div>

              {instLoading ? (
                <div className={styles.imageLoading}>Loading instances…</div>
              ) : instances.length === 0 ? (
                <div className={styles.imageLoading}>
                  No instances found for this series.
                </div>
              ) : (
                <div className={styles.imagePair}>
                  <ImageCol title="RAW Dicom Image" badge="badge-warning"
                            instance={currentInstance} variant="raw" />
                  <ImageCol title="Deidentified Image" badge="badge-success"
                            instance={currentInstance} variant="deid" />
                </div>
              )}
            </div>
          )}

          {/* Attribute table card */}
          <div className="card" style={{padding:'14px 18px'}}>
            <div className={styles.tagTableHeader}>
              <div className={styles.imageCardTitle}>DICOM Attributes</div>
              <button className={styles.toggleBtn}
                      onClick={() => setShowRaw(v => !v)}
                      title="Toggle PHI visibility">
                {showRaw ? '🙈 Hide RAW PHI' : '👁 Show RAW PHI'}
              </button>
            </div>
            <table className={styles.tagTable}>
              <thead>
                <tr>
                  <th>Attributes</th>
                  <th>TAG</th>
                  <th>RAW</th>
                  <th>Masked / De-identified</th>
                </tr>
              </thead>
              <tbody>
                {PHI_TAG_DEFS.map(def => {
                  const rawVal = formatVal(study[def.source]);
                  // Determine the masked/de-identified value
                  let maskedVal;
                  if (def.keep) {
                    maskedVal = rawVal;     // K action — kept unchanged
                  } else if (def.deidSource) {
                    maskedVal = formatVal(study[def.deidSource]);
                  } else if (def.deidValue) {
                    maskedVal = def.deidValue;
                  } else if (def.masker) {
                    maskedVal = def.masker(rawVal);
                  } else {
                    maskedVal = '****';
                  }
                  const displayedRaw = showRaw ? rawVal : maskValue(rawVal);
                  return (
                    <tr key={def.tag}>
                      <td>{def.attr}{def.suffix && rawVal ? def.suffix : ''}</td>
                      <td className={styles.tagCell}>{def.tag}</td>
                      <td className={showRaw ? styles.rawCell : styles.maskedCell}>
                        {displayedRaw}
                      </td>
                      <td className={styles.deidCell}>{maskedVal}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

        </div>
      </div>
    </div>
  );
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function Field({label, value}) {
  return (
    <div className={styles.field}>
      <div className={styles.fieldLabel}>{label}</div>
      <div className={styles.fieldValue}>{value ?? '—'}</div>
    </div>
  );
}

function ImageCol({title, badge, instance, variant}) {
  const [errored, setErrored] = useState(false);
  useEffect(() => { setErrored(false); }, [instance?.id, variant]);
  if (!instance) return null;
  const url = previewUrl(instance.id, variant);
  const filePath = variant === 'raw' ? instance.filePath : instance.deidFilePath;
  return (
    <div className={styles.imageCol}>
      <div className={styles.imageColHeader}>
        <span className={`badge ${badge}`}>{title}</span>
        <span className={styles.imageMeta}>
          {instance.rows ?? '?'}×{instance.cols ?? '?'} ·
          {instance.bitsAllocated ?? '?'}bit · {instance.photometricInterp || '—'}
        </span>
      </div>
      <div className={styles.imageBox}>
        {errored ? (
          <div className={styles.imageError}>
            ⚠ Could not render preview
            <div className={styles.imageErrorPath}>{filePath || 'no file path recorded'}</div>
          </div>
        ) : (
          <img src={url} alt={`${variant} preview`}
               loading="lazy"
               onError={() => setErrored(true)} />
        )}
      </div>
      <div className={styles.imagePath}>
        {filePath ? <code title={filePath}>{filePath.split('/').pop()}</code> : <em>no path</em>}
      </div>
    </div>
  );
}

function formatVal(v) {
  if (v == null || v === '') return '—';
  if (typeof v === 'boolean') return v ? 'YES' : 'NO';
  return String(v);
}

function maskValue(v) {
  if (!v || v === '—') return '—';
  return '*'.repeat(Math.min(8, Math.max(4, String(v).length / 2)));
}
