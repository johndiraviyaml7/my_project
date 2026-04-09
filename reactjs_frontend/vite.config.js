import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Spring Boot API
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      // Orthanc proxy (DICOMweb for OHIF)
      '/orthanc': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  define: {
    // These can be overridden by a .env file
    'import.meta.env.VITE_API_URL':    JSON.stringify(process.env.VITE_API_URL    || 'http://localhost:8080'),
    'import.meta.env.VITE_PARSER_URL': JSON.stringify(process.env.VITE_PARSER_URL || 'http://localhost:8000'),
    'import.meta.env.VITE_PAS_URL':    JSON.stringify(process.env.VITE_PAS_URL    || 'http://localhost:8444'),
    'import.meta.env.VITE_OHIF_URL':   JSON.stringify(process.env.VITE_OHIF_URL   || 'http://localhost:3000'),
    'import.meta.env.VITE_WADO_ROOT':  JSON.stringify(process.env.VITE_WADO_ROOT  || 'http://localhost:8080/orthanc/dicom-web'),
  },
});
