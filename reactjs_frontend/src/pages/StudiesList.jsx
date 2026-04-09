import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api/apiService';
import styles from './StudiesList.module.css';

export default function StudiesList() {
  const { subjectId } = useParams();
  const navigate = useNavigate();
  const [subject, setSubject] = useState(null);
  const [studies, setStudies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search,  setSearch]  = useState('');

  useEffect(() => {
    Promise.all([api.getSubject(subjectId), api.getStudies(subjectId)])
      .then(([s, st]) => { setSubject(s); setStudies(st); })
      .finally(() => setLoading(false));
  }, [subjectId]);

  const filtered = studies.filter(s =>
    !search ||
    s.studyDescription?.toLowerCase().includes(search.toLowerCase()) ||
    s.studyInstanceUid?.includes(search)
  );

  if (loading) return <div style={{ padding: '60px', textAlign: 'center', color: '#6366f1' }}>Loading…</div>;

  return (
    <div className={styles.page}>
      <div className={styles.breadcrumb}>
        <button onClick={() => navigate('/dicom')} className={styles.breadLink}>Devices</button>
        <span className={styles.sep}>›</span>
        <button onClick={() => navigate(`/dicom/subject/${subjectId}`)} className={styles.breadLink}>
          {subject?.subjectId || 'Subject'}
        </button>
        <span className={styles.sep}>›</span>
        <span className={styles.breadCurrent}>Studies</span>
      </div>

      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Studies</h1>
          <p className={styles.subtitle}>{subject?.subjectId} · {studies.length} studies</p>
        </div>
        <input className={styles.search} placeholder="Search studies…"
          value={search} onChange={e => setSearch(e.target.value)} />
      </div>

      <div className={styles.grid}>
        {filtered.map(study => (
          <div key={study.id} className={styles.card}
            onClick={() => navigate(`/dicom/studies/${study.id}`)}>

            {/* Viewer badge */}
            <div className={study.hasViewer ? styles.viewerBadge : styles.pendingBadge}>
              {study.hasViewer ? '🔬 OHIF Viewer Ready' : '⏳ Awaiting Orthanc'}
            </div>

            <div className={styles.cardBody}>
              <div className={styles.cardTitle}>{study.studyDescription || 'No description'}</div>
              <div className={styles.cardMeta}>
                <MetaChip label="Date"    value={study.studyDate || '—'} />
                <MetaChip label="Series"  value={study.totalSeries} />
                <MetaChip label="Images"  value={study.totalInstances} />
              </div>
              <div className={styles.uidLine}>
                UID: <code>{study.studyInstanceUid?.slice(-24)}…</code>
              </div>
            </div>

            <div className={styles.cardFooter}>
              <button className={styles.openBtn}>Open Study →</button>
              {study.hasViewer && study.ohifViewerUrl && (
                <a href={study.ohifViewerUrl} target="_blank" rel="noreferrer"
                  className={styles.ohifBtn} onClick={e => e.stopPropagation()}>
                  🔬 OHIF
                </a>
              )}
            </div>
          </div>
        ))}
        {filtered.length === 0 && <div className={styles.empty}>No studies found</div>}
      </div>
    </div>
  );
}

function MetaChip({ label, value }) {
  return (
    <div className={styles.chip}>
      <span className={styles.chipLabel}>{label}</span>
      <span className={styles.chipValue}>{value}</span>
    </div>
  );
}
