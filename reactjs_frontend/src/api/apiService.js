// QuantixMed DICOM Frontend — API Service v2
// JPEG removed. All viewing via Orthanc + OHIF Viewer.
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

class ApiService {
  constructor() { this.baseUrl = BASE_URL; }

  _headers() {
    const token = localStorage.getItem('qm_token');
    return { 'Content-Type': 'application/json',
             ...(token ? { Authorization: `Bearer ${token}` } : {}) };
  }

  async _req(method, path, body = null) {
    const opts = { method, headers: this._headers() };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(`${this.baseUrl}${path}`, opts);
    if (res.status === 204) return null;
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
    return res.json();
  }

  // Auth
  login(u, p)              { return this._req('POST', '/api/auth/login', { username: u, password: p }); }

  // PACS Devices
  getDevices()             { return this._req('GET',  '/api/devices'); }
  getDevice(id)            { return this._req('GET',  `/api/devices/${id}`); }
  createDevice(d)          { return this._req('POST', '/api/devices', d); }
  updateDevice(id, d)      { return this._req('PUT',  `/api/devices/${id}`, d); }
  deleteDevice(id)         { return this._req('DELETE',`/api/devices/${id}`); }

  // Subjects
  getSubjects(deviceId)    { return this._req('GET', `/api/devices/${deviceId}/subjects`); }
  getSubject(id)           { return this._req('GET', `/api/subjects/${id}`); }

  // Studies (includes orthancStudyId + ohifViewerUrl)
  getStudies(subjectId)    { return this._req('GET', `/api/subjects/${subjectId}/studies`); }
  getStudy(id)             { return this._req('GET', `/api/studies/${id}`); }

  // Series (includes orthancSeriesId)
  getSeries(studyId)       { return this._req('GET', `/api/studies/${studyId}/series`); }
  getSeriesById(id)        { return this._req('GET', `/api/series/${id}`); }

  // Instances (includes orthancInstanceId)
  getInstances(seriesId)   { return this._req('GET', `/api/series/${seriesId}/instances`); }
  getInstance(id)          { return this._req('GET', `/api/instances/${id}`); }

  // Orthanc proxy
  getOrthancStatus()       { return this._req('GET', '/orthanc/status'); }
  getViewerUrl(orthancStudyId) { return this._req('GET', `/orthanc/viewer-url/${orthancStudyId}`); }
}

export const api = new ApiService();
export default api;
