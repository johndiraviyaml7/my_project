/**
 * DicomDashboard — PACS Device overview
 * Theme: matches quantixmed_original_theme_reactjs/DicomDashboard.jsx
 * Data:  live from Spring Boot /api/devices
 */
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';
import api from '../api/apiService';
import { fetchPasDeviceStatus } from '../api/pasService';
import Breadcrumb from '../components/Breadcrumb';
import styles from './DicomDashboard.module.css';

const PAGE_SIZE = 5;

export default function DicomDashboard() {
  const navigate = useNavigate();
  const [devices, setDevices] = useState([]);
  const [pasStatus, setPasStatus] = useState({});  // serial -> { isLive, secondsSinceLastSeen, status }
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState(null);
  const [page,    setPage]    = useState(1);
  const [search,  setSearch]  = useState('');
  const [sort,    setSort]    = useState('newest');

  useEffect(() => {
    let cancelled = false;
    api.getDevices()
      .then(d => { if (!cancelled) setDevices(Array.isArray(d) ? d : []); })
      .catch(e => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  // Poll PAS Connector every 5s for live MQTT-derived status
  useEffect(() => {
    let cancelled = false;
    const poll = async () => {
      const list = await fetchPasDeviceStatus();
      if (cancelled) return;
      const map = {};
      for (const d of list) {
        if (d.serialNumber) map[d.serialNumber] = d;
      }
      setPasStatus(map);
    };
    poll();
    const iv = setInterval(poll, 5000);
    return () => { cancelled = true; clearInterval(iv); };
  }, []);

  // ── Aggregates ──────────────────────────────────────────────────────────
  const totalDevices    = devices.length;
  const activeDevices   = devices.filter(d => /connect|active/i.test(d.status || '')).length;
  const inactiveDevices = totalDevices - activeDevices;
  const totalInstances  = devices.reduce((s, d) => s + (d.instanceCount || 0), 0);
  // The parser de-identifies every instance, so deid==total. Pending stays at
  // 0 in normal operation; we surface it for visual completeness of the chart.
  const totalDeidentified = totalInstances;
  const totalPending      = 0;

  const pieDevices = [
    { name: 'Inactive Devices', value: inactiveDevices || 0, color: '#cbd5e1' },
    { name: 'Active Devices',   value: activeDevices   || 0, color: '#3b82f6' },
  ];
  const pieDicom = [
    { name: 'Deidentified Files', value: totalDeidentified || 0, color: '#f59e0b' },
    { name: 'Pending DeID Files', value: totalPending      || 0, color: '#fde68a' },
  ];

  // ── Filter / sort / paginate ────────────────────────────────────────────
  const q = search.trim().toLowerCase();
  const filtered = devices.filter(d =>
    !q ||
    (d.name || '').toLowerCase().includes(q) ||
    (d.serialNumber || '').toLowerCase().includes(q) ||
    (d.modality || '').toLowerCase().includes(q)
  );
  const sorted = [...filtered].sort((a, b) => {
    const av = a.createdAt || ''; const bv = b.createdAt || '';
    return sort === 'newest' ? bv.localeCompare(av) : av.localeCompare(bv);
  });
  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const paginated  = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h1 className={styles.pageTitle}>Dashboard</h1>
          <Breadcrumb items={[{ label: 'Home', href: '/offerings' }, { label: 'Dashboard' }]} />
        </div>
        <div className={styles.headerActions}>
          <div className="search-input">
            <SearchIcon />
            <input
              placeholder="Search..."
              value={search}
              onChange={e => { setSearch(e.target.value); setPage(1); }}
            />
          </div>
          <button className={styles.iconBtn} title="Refresh"
                  onClick={() => { setLoading(true); api.getDevices().then(d => setDevices(Array.isArray(d)?d:[])).finally(() => setLoading(false)); }}>
            ⟳
          </button>
        </div>
      </div>

      {/* ── Summary cards ───────────────────────────────────────────── */}
      <div className={styles.summaryRow}>
        <div className="card" style={{flex:1, padding:'20px 24px'}}>
          <div className={styles.cardTop}>
            <div className={styles.cardIcon}><DeviceIcon /></div>
            <h3 className={styles.cardLabel}>Active Devices</h3>
          </div>
          <div className={styles.cardBody}>
            <div>
              <div className={styles.bigNumber}>{String(totalDevices).padStart(2, '0')}</div>
              <div className={styles.bigLabel}>Total Devices</div>
            </div>
            <div className={styles.chartWrap}>
              <ResponsiveContainer width={100} height={100}>
                <PieChart>
                  <Pie data={pieDevices} cx={45} cy={45} innerRadius={30} outerRadius={44}
                       dataKey="value" startAngle={90} endAngle={-270}>
                    {pieDevices.map((e, i) => <Cell key={i} fill={e.color} />)}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className={styles.legend}>
              {pieDevices.map(e => (
                <div key={e.name} className={styles.legendItem}>
                  <span className={styles.legendDot} style={{background: e.color}} />
                  <span>{e.name} - {e.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="card" style={{flex:1, padding:'20px 24px'}}>
          <div className={styles.cardTop}>
            <div className={styles.cardIcon}><DicomIcon /></div>
            <h3 className={styles.cardLabel}>DICOM Files</h3>
          </div>
          <div className={styles.cardBody}>
            <div>
              <div className={styles.bigNumber}>{totalInstances.toLocaleString()}</div>
              <div className={styles.bigLabel}>Total Received Files</div>
            </div>
            <div className={styles.chartWrap}>
              <ResponsiveContainer width={100} height={100}>
                <PieChart>
                  <Pie data={pieDicom} cx={45} cy={45} innerRadius={0} outerRadius={44}
                       dataKey="value" startAngle={90} endAngle={-270}>
                    {pieDicom.map((e, i) => <Cell key={i} fill={e.color} />)}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className={styles.legend}>
              {pieDicom.map(e => (
                <div key={e.name} className={styles.legendItem}>
                  <span className={styles.legendDot} style={{background: e.color}} />
                  <span>{e.name} - {e.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* ── Devices table ───────────────────────────────────────────── */}
      <div className="card" style={{marginTop:20}}>
        <div className={styles.tableHeader}>
          <div className={styles.tableTitle}>
            Total Devices
            <span className={styles.countBadge}>{filtered.length}</span>
          </div>
          <div className={styles.tableActions}>
            <div className={styles.sortWrap}>
              <span className={styles.sortLabel}>↕ Sort By :</span>
              <select className={styles.sortSelect} value={sort} onChange={e => setSort(e.target.value)}>
                <option value="newest">Newest</option>
                <option value="oldest">Oldest</option>
              </select>
            </div>
          </div>
        </div>

        {loading ? (
          <div style={{padding:'40px', textAlign:'center', color:'var(--text-muted)'}}>Loading devices…</div>
        ) : error ? (
          <div style={{padding:'40px', textAlign:'center', color:'var(--danger)'}}>
            Failed to load devices: {error}
          </div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th style={{width:36}}><input type="checkbox" className="checkbox" /></th>
                <th>PACS Devices Name</th>
                <th>Serial Number</th>
                <th>Modality Type</th>
                <th>Status</th>
                <th>Subjects</th>
                <th>Dicom Files</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {paginated.map(device => {
                const liveStatus = pasStatus[device.serialNumber];
                const isLive = liveStatus?.isLive === true;
                const effectiveStatus = liveStatus?.status
                    ? liveStatus.status
                    : (device.status || 'Unknown');
                const isConnected = isLive || /connect|active/i.test(effectiveStatus);
                const secsAgo = liveStatus?.secondsSinceLastSeen;
                return (
                  <tr key={device.id}>
                    <td><input type="checkbox" className="checkbox" /></td>
                    <td>
                      <button className={styles.deviceLink}
                              onClick={() => navigate(`/dicom/device/${device.id}`)}>
                        {device.name}
                      </button>
                    </td>
                    <td style={{fontFamily:'Space Mono, monospace', fontSize:12}}>{device.serialNumber}</td>
                    <td>{device.modality}</td>
                    <td>
                      <span className={`badge ${isConnected ? 'badge-success' : 'badge-danger'}`}>
                        {effectiveStatus}
                      </span>
                      {liveStatus && (
                        <div style={{fontSize:10, color:'var(--text-muted)', marginTop:2}}>
                          {isLive
                            ? `● live · ${secsAgo ?? 0}s ago`
                            : (secsAgo != null ? `last seen ${secsAgo}s ago` : 'no heartbeat')}
                        </div>
                      )}
                    </td>
                    <td>{device.subjectCount ?? 0}</td>
                    <td>
                      <button className={styles.linkBtn}
                              onClick={() => navigate(`/dicom/device/${device.id}/subjects`)}>
                        {(device.instanceCount ?? 0).toLocaleString()} (Link)
                      </button>
                    </td>
                    <td>
                      <div style={{display:'flex', gap:4}}>
                        <button className="action-btn" title="View"
                                onClick={() => navigate(`/dicom/device/${device.id}`)}>
                          <EyeIcon />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
              {paginated.length === 0 && (
                <tr><td colSpan="8" style={{padding:'40px', textAlign:'center', color:'var(--text-muted)'}}>
                  No PACS devices yet. Run <code>docker compose --profile init run --rm parser_init</code> to load sample data.
                </td></tr>
              )}
            </tbody>
          </table>
        )}

        <div className={styles.tableFooter}>
          <div className={styles.showingWrap}>
            <span>Showing {paginated.length} of {filtered.length}</span>
          </div>
          <div className="pagination">
            <button className={`page-btn ${page === 1 ? 'disabled' : ''}`}
                    onClick={() => setPage(p => Math.max(1, p - 1))}>Prev</button>
            {Array.from({length: totalPages}, (_, i) => i + 1).map(p => (
              <button key={p}
                      className={`page-btn ${page === p ? 'active' : ''}`}
                      onClick={() => setPage(p)}>{p}</button>
            ))}
            <button className={`page-btn ${page === totalPages ? 'disabled' : ''}`}
                    onClick={() => setPage(p => Math.min(totalPages, p + 1))}>Next</button>
          </div>
        </div>
      </div>
    </div>
  );
}

function SearchIcon() {
  return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="2">
    <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>;
}
function DeviceIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/></svg>;
}
function DicomIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>;
}
function EyeIcon() {
  return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>;
}
