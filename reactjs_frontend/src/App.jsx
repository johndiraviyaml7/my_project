import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import AppLayout from './components/AppLayout';
import Login from './pages/Login';
import Offerings from './pages/Offerings';
import DicomDashboard from './pages/DicomDashboard';
import DeviceDetail from './pages/DeviceDetail';
import SubjectsList from './pages/SubjectsList';
import SubjectDetail from './pages/SubjectDetail';
import StudiesList from './pages/StudiesList';
import StudyDetail from './pages/StudyDetail';
import DicomSeriesDetail from './pages/DicomSeriesDetail';

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Navigate to="/offerings" replace />} />
          <Route element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }>
            <Route path="/offerings" element={<Offerings />} />

            {/* PACS Devices */}
            <Route path="/dicom" element={<DicomDashboard />} />
            <Route path="/dicom/device/:deviceId" element={<DeviceDetail />} />

            {/* Subjects — NEW in workflow */}
            <Route path="/dicom/device/:deviceId/subjects" element={<SubjectsList />} />
            <Route path="/dicom/subject/:subjectId" element={<SubjectDetail />} />

            {/* Studies */}
            <Route path="/dicom/subject/:subjectId/studies" element={<StudiesList />} />
            <Route path="/dicom/studies/:studyId" element={<StudyDetail />} />

            {/* Series & Instances */}
            <Route path="/dicom/studies/:studyId/series/:seriesId" element={<DicomSeriesDetail />} />
          </Route>
          <Route path="*" element={<Navigate to="/offerings" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
