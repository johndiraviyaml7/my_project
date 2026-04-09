/**
 * OHIFViewer — Full-screen OHIF Viewer embedded in an iframe.
 * 
 * Works with ANY study in Orthanc — does NOT require the study to be
 * in our PostgreSQL database. Constructs the OHIF URL from the
 * StudyInstanceUID using the Spring Boot Orthanc proxy as wadoRsRoot.
 */
import React, { useState, useEffect } from 'react';
import { buildOhifUrl } from '../api/orthancService';
import styles from './OHIFViewer.module.css';

export default function OHIFViewer({ studyInstanceUid, ohifViewerUrl, onClose }) {
  const [loading, setLoading] = useState(true);

  // Prefer the server-built URL (StudyService constructs it using the
  // verified Orthanc MainDicomTags UID after upload).  Fall back to a
  // client-side build if the server didn't supply one.
  const viewerUrl = ohifViewerUrl || buildOhifUrl(studyInstanceUid);

  useEffect(() => {
    setLoading(true);
  }, [studyInstanceUid]);

  return (
    <div className={styles.overlay}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.logo}>🔬</span>
          <div>
            <div className={styles.title}>OHIF Medical Image Viewer</div>
            <div className={styles.subtitle}>
              Study: <code>{studyInstanceUid ? studyInstanceUid.slice(-28) + '…' : '(no UID!)'}</code>
            </div>
            <div className={styles.subtitle} style={{fontSize:10, opacity:0.7}}>
              <code>{viewerUrl}</code>
            </div>
          </div>
        </div>
        <div className={styles.headerRight}>
          <a href={viewerUrl} target="_blank" rel="noreferrer" className={styles.fullscreenBtn}>
            ⤢ Full Screen
          </a>
          <button className={styles.closeBtn} onClick={onClose}>✕ Close</button>
        </div>
      </div>

      <div className={styles.body}>
        {loading && (
          <div className={styles.loadingPane}>
            <div className={styles.spinner} />
            <div className={styles.loadingTitle}>Loading OHIF Viewer…</div>
            <div className={styles.loadingHint}>
              Fetching DICOM data from Orthanc via DICOMweb
            </div>
          </div>
        )}
        <iframe
          src={viewerUrl}
          title="OHIF DICOM Viewer"
          className={`${styles.iframe} ${loading ? styles.iframeHidden : ''}`}
          onLoad={() => setLoading(false)}
          allow="fullscreen"
        />
      </div>
    </div>
  );
}
