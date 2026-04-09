import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api/apiService';
import styles from './SubjectsList.module.css';

const STATUS_COLORS = {
  Loaded:      { color: '#16a34a', bg: '#dcfce7' },
  Processing:  { color: '#d97706', bg: '#fef3c7' },
  Pending:     { color: '#6366f1', bg: '#eef2ff' },
  Failed:      { color: '#dc2626', bg: '#fee2e2' },
};

export default function SubjectsList() {
  const { deviceId } = useParams();
  const navigate = useNavigate();
  const [subjects, setSubjects] = useState([]);
  const [device, setDevice] = useState(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([
      api.getDevice(deviceId),
      api.getSubjects(deviceId),
    ]).then(([dev, subs]) => {
      setDevice(dev);
      setSubjects(subs);
    }).catch(e => {
      setError(e.message);
    }).finally(() => setLoading(false));
  }, [deviceId]);

  const filtered = subjects.filter(s =>
    !search || s.subjectId?.toLowerCase().includes(search.toLowerCase()) ||
    s.collection?.toLowerCase().includes(search.toLowerCase()) ||
    s.site?.toLowerCase().includes(search.toLowerCase())
  );

  if (loading) return <LoadingState />;
  if (error)   return <ErrorState message={error} />;

  return (
    <div className={styles.page}>
      {/* Breadcrumb */}
      <div className={styles.breadcrumb}>
        <button onClick={() => navigate('/dicom')} className={styles.breadLink}>PACS Devices</button>
        <span className={styles.sep}>›</span>
        <button onClick={() => navigate(`/dicom/device/${deviceId}`)} className={styles.breadLink}>
          {device?.name || 'Device'}
        </button>
        <span className={styles.sep}>›</span>
        <span className={styles.breadCurrent}>Subjects</span>
      </div>

      {/* Header */}
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Subjects</h1>
          <p className={styles.subtitle}>
            {device?.name} · {subjects.length} subjects loaded
          </p>
        </div>
        <div className={styles.headerActions}>
          <input
            className={styles.search}
            placeholder="Search subjects…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
      </div>

      {/* Stats row */}
      <div className={styles.statsRow}>
        {[
          { label: 'Total Subjects', value: subjects.length },
          { label: 'Loaded', value: subjects.filter(s => s.loadStatus === 'Loaded').length },
          { label: 'Total Studies', value: subjects.reduce((a, s) => a + (s.totalStudies || 0), 0) },
          { label: 'Total Instances', value: subjects.reduce((a, s) => a + (s.totalInstances || 0), 0).toLocaleString() },
        ].map(st => (
          <div key={st.label} className={styles.statCard}>
            <div className={styles.statValue}>{st.value}</div>
            <div className={styles.statLabel}>{st.label}</div>
          </div>
        ))}
      </div>

      {/* Table */}
      <div className={styles.tableCard}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Subject ID</th>
              <th>Collection</th>
              <th>Site</th>
              <th>Sex</th>
              <th>Age</th>
              <th>Studies</th>
              <th>Series</th>
              <th>Instances</th>
              <th>Status</th>
              <th>De-identified ID</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(subj => {
              const sc = STATUS_COLORS[subj.loadStatus] || STATUS_COLORS.Pending;
              return (
                <tr key={subj.id} className={styles.row}
                    onClick={() => navigate(`/dicom/subject/${subj.id}`)}>
                  <td><span className={styles.subjectId}>{subj.subjectId}</span></td>
                  <td>{subj.collection || '—'}</td>
                  <td>{subj.site || '—'}</td>
                  <td>{subj.patientSex || '—'}</td>
                  <td>{subj.patientAge || '—'}</td>
                  <td><span className={styles.count}>{subj.totalStudies || 0}</span></td>
                  <td><span className={styles.count}>{subj.totalSeries || 0}</span></td>
                  <td><span className={styles.count}>{(subj.totalInstances || 0).toLocaleString()}</span></td>
                  <td>
                    <span className={styles.badge}
                      style={{ color: sc.color, background: sc.bg }}>
                      {subj.loadStatus}
                    </span>
                  </td>
                  <td><code className={styles.deidId}>{subj.deidSubjectId || '—'}</code></td>
                  <td>
                    <button className={styles.openBtn}
                      onClick={e => { e.stopPropagation(); navigate(`/dicom/subject/${subj.id}`); }}>
                      Open →
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <div className={styles.empty}>No subjects found{search ? ` matching "${search}"` : ''}</div>
        )}
      </div>
    </div>
  );
}

function LoadingState() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: '60px', color: '#6366f1' }}>
      <div>Loading subjects…</div>
    </div>
  );
}

function ErrorState({ message }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: '60px', color: '#dc2626' }}>
      <div>Error: {message}</div>
    </div>
  );
}
