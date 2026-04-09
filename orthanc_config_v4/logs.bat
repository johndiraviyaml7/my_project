@echo off
echo === Orthanc logs (last 80 lines) ===
docker logs --tail=80 quantixmed_orthanc 2>&1
echo.
echo === OHIF logs (last 20 lines) ===
docker logs --tail=20 quantixmed_ohif 2>&1
echo.
pause
