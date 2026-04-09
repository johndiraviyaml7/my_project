import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api/apiService';
import styles from './DeviceDetail.module.css';

export default function DeviceDetail() {
  const { deviceId } = useParams();
  const navigate     = useNavigate();
  const [device,   setDevice]   = useState(null);
  const [subjects, setSubjects] = useState([]);
  const [loading,  setLoading]  = useState(true);

  useEffect(() => {
    Promise.all([
      api.getDevice(deviceId),
      api.getSubjects(deviceId),
    ]).then(([dev, subs]) => {
      setDevice(dev);
      setSubjects(subs);
    }).finally(() => setLoading(false));
  }, [deviceId]);

  if (loading) return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: '60px', color: '#6366f1' }}>Loading…</div>
  );
  if (!device) return <div style={{ padding: '40px', color: '#dc2626' }}>Device not found</div>;

  const loaded      = subjects.filter(s => s.loadStatus === 'Loaded').length;
  const totalStudies = subjects.reduce((a, s) => a + (s.totalStudies || 0), 0);
  const totalInst    = subjects.reduce((a, s) => a + (s.totalInstances || 0), 0);

  return (
    <div className={styles.page}>
      {/* Breadcrumb */}
      <div className={styles.breadcrumb}>
        <button onClick={() => navigate('/dicom')} className={styles.breadLink}>PACS Devices</button>
        <span className={styles.sep}>›</span>
        <span className={styles.breadCurrent}>{device.name}</span>
      </div>

      {/* Device info */}
      <div className={styles.deviceCard}>
        <div className={styles.deviceLeft}>
          <div className={styles.deviceIcon}>🖥</div>
          <div>
            <div className={styles.deviceName}>{device.name}</div>
            <div className={styles.deviceSerial}>{device.serialNumber}</div>
            <span className={styles.statusBadge}
              style={device.status === 'Active'
                ? { color: '#16a34a', background: '#dcfce7' }
                : { color: '#94a3b8', background: '#f1f5f9' }}>
              {device.status}
            </span>
          </div>
        </div>
        <div className={styles.deviceMeta}>
          <MetaPair label="Modality"     value={device.modality} />
          <MetaPair label="Manufacturer" value={device.manufacturer} />
          <MetaPair label="Model"        value={device.model} />
          <MetaPair label="Location"     value={device.location} />
          <MetaPair label="AE Title"     value={device.aeTitle} />
          <MetaPair label="IP : Port"    value={device.ipAddress ? `${device.ipAddress}:${device.port}` : '—'} />
        </div>
      </div>

      {/* Stats */}
      <div className={styles.statsRow}>
        {[
          { label: 'Subjects',   value: subjects.length },
          { label: 'Loaded',     value: loaded },
          { label: 'Studies',    value: totalStudies },
          { label: 'Instances',  value: totalInst.toLocaleString() },
        ].map(s => (
          <div key={s.label} className={styles.statCard}>
            <div className={styles.statValue}>{s.value}</div>
            <div className={styles.statLabel}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Recent subjects preview */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>Subjects ({subjects.length})</h2>
          <button className={styles.viewAllBtn}
            onClick={() => navigate(`/dicom/device/${deviceId}/subjects`)}>
            View All Subjects →
          </button>
        </div>

        <div className={styles.subjectsTable}>
          <table className={styles.tbl}>
            <thead>
              <tr>
                <th>Subject ID</th>
                <th>Collection</th>
                <th>Sex</th>
                <th>Age</th>
                <th>Studies</th>
                <th>Series</th>
                <th>Instances</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {subjects.slice(0, 10).map(s => (
                <tr key={s.id} className={styles.row}
                  onClick={() => navigate(`/dicom/subject/${s.id}`)}>
                  <td><span className={styles.subjectId}>{s.subjectId}</span></td>
                  <td>{s.collection || '—'}</td>
                  <td>{s.patientSex || '—'}</td>
                  <td>{s.patientAge || '—'}</td>
                  <td>{s.totalStudies || 0}</td>
                  <td>{s.totalSeries || 0}</td>
                  <td>{(s.totalInstances || 0).toLocaleString()}</td>
                  <td>
                    <span className={styles.statusPill}
                      style={s.loadStatus === 'Loaded'
                        ? { color: '#16a34a', background: '#dcfce7' }
                        : { color: '#d97706', background: '#fef3c7' }}>
                      {s.loadStatus}
                    </span>
                  </td>
                  <td>
                    <button className={styles.openBtn}
                      onClick={e => { e.stopPropagation(); navigate(`/dicom/subject/${s.id}`); }}>
                      Open
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {subjects.length === 0 && (
            <div className={styles.empty}>No subjects loaded yet for this device</div>
          )}
          {subjects.length > 10 && (
            <div className={styles.moreRow}>
              <button onClick={() => navigate(`/dicom/device/${deviceId}/subjects`)}>
                View all {subjects.length} subjects →
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function MetaPair({ label, value }) {
  return (
    <div className={styles.metaPair}>
      <div className={styles.metaLabel}>{label}</div>
      <div className={styles.metaValue}>{value || '—'}</div>
    </div>
  );
}
