@echo off
echo.
echo ── Containers ───────────────────────────────────────────────────────────
docker ps -a --filter "name=quantixmed" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"
echo.
echo ── Orthanc system info ───────────────────────────────────────────────────
curl -s -u admin:admin http://localhost:8042/system 2>nul
echo.
echo ── DICOMweb available ────────────────────────────────────────────────────
curl -s -o nul -w "DICOMweb HTTP status: %%{http_code}" -u admin:admin http://localhost:8042/dicom-web/studies 2>nul
echo.
pause
