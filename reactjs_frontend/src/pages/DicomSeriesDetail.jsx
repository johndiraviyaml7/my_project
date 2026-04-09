import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api/apiService';
import OHIFViewer from '../components/OHIFViewer';
import styles from './DicomSeriesDetail.module.css';

export default function DicomSeriesDetail() {
  const { studyId, seriesId } = useParams();
  const navigate = useNavigate();
  const [study,     setStudy]     = useState(null);
  const [series,    setSeries]    = useState(null);
  const [instances, setInstances] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [showViewer,setShowViewer]= useState(false);
  const [selected,  setSelected]  = useState(null);

  useEffect(() => {
    Promise.all([
      api.getStudy(studyId),
      api.getSeriesById(seriesId),
      api.getInstances(seriesId),
    ]).then(([st, sr, insts]) => {
      setStudy(st); setSeries(sr); setInstances(insts);
      if (insts.length > 0) setSelected(insts[0]);
    }).finally(() => setLoading(false));
  }, [studyId, seriesId]);

  if (loading) return <div style={{ padding: '60px', textAlign: 'center', color: '#6366f1' }}>Loading…</div>;
  if (!series) return <div style={{ padding: '40px', color: '#dc2626' }}>Series not found</div>;

  return (
    <div className={styles.page}>
      {showViewer && study && (
        <OHIFViewer
          studyInstanceUid={study.orthancStudyUid || study.deidStudyInstanceUid}
          orthancStudyId={study.orthancStudyId}
          ohifViewerUrl={study.ohifViewerUrl}
          onClose={() => setShowViewer(false)}
        />
      )}

      {/* Breadcrumb */}
      <div className={styles.breadcrumb}>
        <button onClick={() => navigate('/dicom')} className={styles.breadLink}>Devices</button>
        <span className={styles.sep}>›</span>
        {study && (
          <>
            <button onClick={() => navigate(`/dicom/subject/${study.subjectId}`)} className={styles.breadLink}>
              {study.subjectLabel}
            </button>
            <span className={styles.sep}>›</span>
            <button onClick={() => navigate(`/dicom/studies/${studyId}`)} className={styles.breadLink}>Study</button>
            <span className={styles.sep}>›</span>
          </>
        )}
        <span className={styles.breadCurrent}>{series.seriesDescription || `Series ${series.seriesNumber}`}</span>
      </div>

      {/* Series header */}
      <div className={styles.seriesHeader}>
        <div>
          <div className={styles.seriesTitle}>
            {series.seriesDescription || `Series ${series.seriesNumber}`}
            <span className={styles.modalityTag}>{series.modality}</span>
          </div>
          <div className={styles.seriesMeta}>
            {series.bodyPartExamined && <MetaSpan label="Body Part"  value={series.bodyPartExamined} />}
            {series.protocolName     && <MetaSpan label="Protocol"   value={series.protocolName} />}
            {series.sliceThickness   && <MetaSpan label="Thickness"  value={`${series.sliceThickness} mm`} />}
            {series.manufacturer     && <MetaSpan label="Vendor"     value={series.manufacturer} />}
            <MetaSpan label="Images" value={series.imageCount} />
          </div>
        </div>

        {/* OHIF open button */}
        <div className={styles.viewerControls}>
          {study?.hasViewer ? (
            <button className={styles.ohifBtn} onClick={() => setShowViewer(true)}>
              🔬 Open OHIF Viewer
            </button>
          ) : (
            <div className={styles.noViewer}>🔬 OHIF not available (Orthanc pending)</div>
          )}
          {series.orthancSeriesId && (
            <div className={styles.orthancId}>Orthanc Series: <code>{series.orthancSeriesId.slice(0,16)}…</code></div>
          )}
        </div>
      </div>

      {/* Instances table + metadata panel */}
      <div className={styles.mainLayout}>
        {/* Instance list */}
        <div className={styles.instanceList}>
          <div className={styles.listTitle}>Instances ({instances.length})</div>
          <div className={styles.listScroll}>
            {instances.map(inst => (
              <div key={inst.id}
                className={`${styles.instRow} ${selected?.id === inst.id ? styles.instActive : ''}`}
                onClick={() => setSelected(inst)}>
                <div className={styles.instNum}>#{inst.instanceNumber}</div>
                <div className={styles.instInfo}>
                  <div className={styles.instDim}>{inst.rows}×{inst.cols}</div>
                  {inst.sliceLocation != null && (
                    <div className={styles.instLoc}>z={Number(inst.sliceLocation).toFixed(1)}</div>
                  )}
                </div>
                {inst.orthancInstanceId && (
                  <div className={styles.instOrthancDot} title={`Orthanc: ${inst.orthancInstanceId}`}>●</div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Metadata panel */}
        {selected && (
          <div className={styles.metaPanel}>
            <div className={styles.metaPanelHeader}>
              Instance #{selected.instanceNumber} — Metadata
              {study?.hasViewer && (
                <button className={styles.ohifSmallBtn} onClick={() => setShowViewer(true)}>
                  🔬 Open in OHIF
                </button>
              )}
            </div>
            <div className={styles.metaGrid}>
              <MetaRow2 label="Instance Number"    value={selected.instanceNumber} />
              <MetaRow2 label="SOP Class UID"      value={selected.sopClassUid} mono />
              <MetaRow2 label="SOP Instance UID"   value={selected.sopInstanceUid} mono />
              <MetaRow2 label="De-id SOP UID"      value={selected.deidSopInstanceUid} mono green />
              {selected.orthancInstanceId && (
                <MetaRow2 label="Orthanc ID" value={selected.orthancInstanceId} mono violet />
              )}
              <MetaRow2 label="Rows"              value={selected.rows} />
              <MetaRow2 label="Columns"           value={selected.cols} />
              <MetaRow2 label="Bits Allocated"    value={selected.bitsAllocated} />
              <MetaRow2 label="Photometric"       value={selected.photometricInterp} />
              <MetaRow2 label="Slice Location"    value={selected.sliceLocation != null ? `${Number(selected.sliceLocation).toFixed(3)} mm` : null} />
              <MetaRow2 label="Acquisition Date"  value={selected.acquisitionDate} />
              <MetaRow2 label="File Size"         value={selected.fileSizeBytes ? `${(selected.fileSizeBytes/1024).toFixed(1)} KB` : null} />
            </div>

            {/* Compliance block */}
            <div className={styles.complianceBlock}>
              <div className={styles.complianceTitle}>De-identification Compliance</div>
              <div className={styles.complianceTags}>
                <span className={styles.compTag}>✅ DICOM PS3.15 2026</span>
                <span className={styles.compTag}>✅ HIPAA §164.514</span>
                <span className={styles.compTag}>✅ FDA 21 CFR Part 11</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function MetaSpan({ label, value }) {
  if (!value && value !== 0) return null;
  return (
    <span className={styles.metaSpan}>
      <span className={styles.msLabel}>{label}:</span>
      <span className={styles.msValue}>{value}</span>
    </span>
  );
}

function MetaRow2({ label, value, mono, green, violet }) {
  if (!value && value !== 0) return null;
  return (
    <div className={styles.mr2}>
      <div className={styles.mr2Label}>{label}</div>
      <div className={styles.mr2Value} style={{
        fontFamily: mono ? 'monospace' : 'inherit',
        fontSize: mono ? '10px' : '12px',
        color: green ? '#16a34a' : violet ? '#6366f1' : undefined,
        wordBreak: 'break-all',
      }}>{String(value)}</div>
    </div>
  );
}
