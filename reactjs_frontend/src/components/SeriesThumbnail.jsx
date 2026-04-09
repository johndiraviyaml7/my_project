/**
 * SeriesThumbnail — renders a WADO-RS rendered JPEG for a DICOM series.
 * 
 * Fetches the first instance UID via QIDO-RS, then requests a rendered
 * JPEG from Orthanc's WADO-RS /rendered endpoint through the Spring Boot proxy.
 * Shows a loading spinner, then the actual DICOM image.
 */
import React, { useState, useEffect } from 'react';
import { qidoInstances, wadoRenderedUrl, TAGS, dicomVal } from '../api/orthancService';

export default function SeriesThumbnail({ studyUID, seriesUID, width = 160, height = 160, className = '' }) {
  const [src,     setSrc]     = useState(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState(false);

  useEffect(() => {
    if (!studyUID || !seriesUID) { setLoading(false); setError(true); return; }
    let cancelled = false;

    setLoading(true); setError(false); setSrc(null);

    qidoInstances(studyUID, seriesUID)
      .then(instances => {
        if (cancelled || !instances?.length) { setError(true); setLoading(false); return; }
        // Pick the middle instance for a more representative thumbnail
        const mid = instances[Math.floor(instances.length / 2)];
        const instanceUID = dicomVal(mid, TAGS.SOPInstanceUID);
        if (!instanceUID) { setError(true); setLoading(false); return; }
        setSrc(wadoRenderedUrl(studyUID, seriesUID, instanceUID, width, height));
      })
      .catch(() => { if (!cancelled) { setError(true); setLoading(false); } });

    return () => { cancelled = true; };
  }, [studyUID, seriesUID]);

  const containerStyle = {
    width, height, background: '#0a0f1e', borderRadius: 6,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    overflow: 'hidden', flexShrink: 0,
  };

  if (error || !src) return (
    <div style={containerStyle} className={className}>
      <span style={{ fontSize: 28, opacity: 0.4 }}>🩻</span>
    </div>
  );

  return (
    <div style={containerStyle} className={className}>
      {loading && (
        <div style={{
          position: 'absolute', display: 'flex',
          alignItems: 'center', justifyContent: 'center',
        }}>
          <Spinner />
        </div>
      )}
      <img
        src={src}
        alt="DICOM thumbnail"
        style={{ width: '100%', height: '100%', objectFit: 'contain', display: loading ? 'none' : 'block' }}
        onLoad={() => setLoading(false)}
        onError={() => { setLoading(false); setError(true); setSrc(null); }}
      />
    </div>
  );
}

function Spinner() {
  return (
    <div style={{
      width: 20, height: 20, borderRadius: '50%',
      border: '2px solid #1e293b', borderTopColor: '#3b82f6',
      animation: 'spin 0.75s linear infinite',
    }} />
  );
}
