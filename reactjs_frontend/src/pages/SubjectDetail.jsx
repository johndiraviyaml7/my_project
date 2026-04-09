/**
 * SubjectDetail
 * ─────────────────────────────────────────────────────────────────
 * Shows one subject and its full hierarchy:
 *   subject ─▶ studies ─▶ series  (expandable)
 *                            └▶ instances (raw + deidentified carousel)
 *
 * Each series row collapses by default. Expanding it fetches the
 * series' instances from Spring Boot, then the carousel renders two
 * <img> tags per instance pulling PNG previews from the dicom_parser
 * FastAPI:
 *
 *   GET /api/instances/{id}/preview?variant=raw
 *   GET /api/instances/{id}/preview?variant=deid
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api/apiService';
import Breadcrumb from '../components/Breadcrumb';
import styles from './SubjectDetail.module.css';

const PARSER_URL = import.meta.env.VITE_PARSER_URL || 'http://localhost:8000';

const previewUrl = (instanceId, variant) =>
  `${PARSER_URL}/api/instances/${instanceId}/preview?variant=${variant}&max_size=512`;

export default function SubjectDetail() {
  const { subjectId } = useParams();
  const navigate = useNavigate();

  const [subject, setSubject] = useState(null);
  const [studies, setStudies] = useState([]);
  // study UUID -> series array
  const [seriesByStudy, setSeriesByStudy] = useState({});
  // series UUID -> instance array
  const [instancesBySeries, setInstancesBySeries] = useState({});
  // expanded series UUID set
  const [expanded, setExpanded] = useState(new Set());
  // current instance index per series
  const [carouselIdx, setCarouselIdx] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // ── Initial load: subject + studies + (eagerly) series for each study ───
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([api.getSubject(subjectId), api.getStudies(subjectId)])
      .then(async ([s, st]) => {
        if (cancelled) return;
        setSubject(s);
        setStudies(Array.isArray(st) ? st : []);
        // Eagerly fetch series for every study so the tree is visible immediately
        const map = {};
        await Promise.all((st || []).map(async study => {
          try {
            map[study.id] = await api.getSeries(study.id);
          } catch {
            map[study.id] = [];
          }
        }));
        if (!cancelled) setSeriesByStudy(map);
      })
      .catch(e => { if (!cancelled) setError(e.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [subjectId]);

  // ── Toggle series expand → lazy-fetch instances ─────────────────────────
  const toggleSeries = useCallback(async (seriesId) => {
    const next = new Set(expanded);
    if (next.has(seriesId)) {
      next.delete(seriesId);
      setExpanded(next);
      return;
    }
    next.add(seriesId);
    setExpanded(next);
    if (!instancesBySeries[seriesId]) {
      try {
        const inst = await api.getInstances(seriesId);
        setInstancesBySeries(prev => ({ ...prev, [seriesId]: inst || [] }));
        setCarouselIdx(prev => ({ ...prev, [seriesId]: 0 }));
      } catch (e) {
        setInstancesBySeries(prev => ({ ...prev, [seriesId]: [] }));
      }
    }
  }, [expanded, instancesBySeries]);

  const stepCarousel = (seriesId, delta) => {
    const list = instancesBySeries[seriesId] || [];
    if (list.length === 0) return;
    setCarouselIdx(prev => {
      const cur = prev[seriesId] ?? 0;
      const next = (cur + delta + list.length) % list.length;
      return { ...prev, [seriesId]: next };
    });
  };

  // ── Render ──────────────────────────────────────────────────────────────
  if (loading) return <div className={styles.loading}>Loading subject…</div>;
  if (error)   return <div className={styles.error}>Failed to load: {error}</div>;
  if (!subject) return <div className={styles.error}>Subject not found.</div>;

  const totalSeries    = Object.values(seriesByStudy).reduce((s, arr) => s + (arr?.length || 0), 0);
  const totalInstances = subject.totalInstances || 0;

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div>
          <h1 className={styles.pageTitle}>Subject {subject.subjectId}</h1>
          <Breadcrumb items={[
            { label: 'Home', href: '/offerings' },
            { label: 'PACS', href: '/dicom' },
            { label: 'Subjects', href: subject.deviceId ? `/dicom/device/${subject.deviceId}/subjects` : '/dicom' },
            { label: subject.subjectId },
          ]}/>
        </div>
      </div>

      {/* ── Subject info card ─────────────────────────────────────── */}
      <div className="card" style={{padding:'20px 24px'}}>
        <div className={styles.infoHeader}>
          <div className={styles.avatar}>{(subject.subjectId || 'S')[0]}</div>
          <div style={{flex:1}}>
            <div className={styles.subjectId}>{subject.subjectId}</div>
            <div className={styles.deidId}>De-identified ID: {subject.deidSubjectId || '—'}</div>
          </div>
          <span className={`badge ${subject.loadStatus === 'Loaded' ? 'badge-success' : 'badge-warning'}`}>
            {subject.loadStatus || 'Unknown'}
          </span>
        </div>

        <div className={styles.metaGrid}>
          <Meta label="Collection"    value={subject.collection} />
          <Meta label="Site"          value={subject.site} />
          <Meta label="Species"       value={subject.speciesDescription} />
          <Meta label="Studies"       value={studies.length} />
          <Meta label="Series"        value={totalSeries} />
          <Meta label="Instances"     value={totalInstances.toLocaleString()} />
        </div>
      </div>

      {/* ── Studies → Series tree ─────────────────────────────────── */}
      {studies.map(study => {
        const seriesList = seriesByStudy[study.id] || [];
        return (
          <div key={study.id} className="card" style={{marginTop:16, padding:'18px 22px'}}>
            <div className={styles.studyHeader}>
              <div>
                <div className={styles.studyDesc}>{study.studyDescription || 'Untitled study'}</div>
                <div className={styles.studyMeta}>
                  <span>📅 {study.studyDate || '—'}</span>
                  <span>🗂 {seriesList.length} series</span>
                  <span>🖼 {study.totalInstances || 0} instances</span>
                  <span className={styles.uid}>UID: ...{(study.studyInstanceUid || '').slice(-24)}</span>
                </div>
              </div>
              <button className={styles.openStudyBtn}
                      onClick={() => navigate(`/dicom/studies/${study.id}`)}>
                Open in OHIF →
              </button>
            </div>

            {/* Series rows */}
            <div className={styles.seriesList}>
              {seriesList.length === 0 && (
                <div className={styles.empty}>No series for this study.</div>
              )}
              {seriesList.map(series => {
                const isOpen = expanded.has(series.id);
                const instances = instancesBySeries[series.id] || [];
                const idx = carouselIdx[series.id] ?? 0;
                const cur = instances[idx];
                return (
                  <div key={series.id} className={styles.seriesRow}>
                    <button className={styles.seriesToggle}
                            onClick={() => toggleSeries(series.id)}>
                      <span className={styles.chevron}>{isOpen ? '▾' : '▸'}</span>
                      <span className={styles.seriesNumber}>#{series.seriesNumber ?? '?'}</span>
                      <span className={styles.seriesDesc}>
                        {series.seriesDescription || 'Untitled series'}
                      </span>
                      <span className={`badge badge-info ${styles.modalityBadge}`}>
                        {series.modality || '—'}
                      </span>
                      <span className={styles.seriesCount}>
                        {series.imageCount ?? '?'} images
                      </span>
                    </button>

                    {isOpen && (
                      <div className={styles.carousel}>
                        {instances.length === 0 ? (
                          <div className={styles.carouselLoading}>Loading instances…</div>
                        ) : (
                          <>
                            <div className={styles.carouselNav}>
                              <button className={styles.navBtn}
                                      onClick={() => stepCarousel(series.id, -1)}
                                      disabled={instances.length < 2}>‹ Prev</button>
                              <div className={styles.carouselCounter}>
                                Instance {idx + 1} of {instances.length}
                                {cur && <span className={styles.instanceNum}>
                                  &nbsp;·&nbsp;#{cur.instanceNumber}
                                </span>}
                              </div>
                              <button className={styles.navBtn}
                                      onClick={() => stepCarousel(series.id, 1)}
                                      disabled={instances.length < 2}>Next ›</button>
                            </div>

                            <div className={styles.imagePair}>
                              <ImageColumn
                                title="RAW"
                                badgeClass="badge-warning"
                                instance={cur}
                                variant="raw"
                              />
                              <ImageColumn
                                title="DE-IDENTIFIED"
                                badgeClass="badge-success"
                                instance={cur}
                                variant="deid"
                              />
                            </div>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}

      {studies.length === 0 && (
        <div className="card" style={{marginTop:16, padding:'40px', textAlign:'center', color:'var(--text-muted)'}}>
          No studies found for this subject. Run the parser pipeline to load data.
        </div>
      )}
    </div>
  );
}

// ── Sub-components ──────────────────────────────────────────────────────────

function Meta({ label, value }) {
  return (
    <div className={styles.metaItem}>
      <div className={styles.metaLabel}>{label}</div>
      <div className={styles.metaValue}>{value || '—'}</div>
    </div>
  );
}

function ImageColumn({ title, badgeClass, instance, variant }) {
  const [errored, setErrored] = useState(false);
  // Reset error state when the instance changes
  useEffect(() => { setErrored(false); }, [instance?.id]);

  if (!instance) return null;
  const url = previewUrl(instance.id, variant);
  const filePath = variant === 'raw' ? instance.filePath : instance.deidFilePath;

  return (
    <div className={styles.imageCol}>
      <div className={styles.imageHeader}>
        <span className={`badge ${badgeClass}`}>{title}</span>
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
               onError={() => setErrored(true)}
               loading="lazy" />
        )}
      </div>
      <div className={styles.imagePath}>
        {filePath ? <code title={filePath}>{filePath.split('/').pop()}</code> : <em>no path</em>}
      </div>
    </div>
  );
}
