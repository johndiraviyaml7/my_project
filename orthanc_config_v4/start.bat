@echo off
echo.
echo ============================================================
echo  QuantixMed — Starting Orthanc + OHIF
echo ============================================================
echo.

echo [1/3] Checking Docker Desktop is running...
docker info >nul 2>&1
if errorlevel 1 (
    echo.
    echo  ERROR: Docker Desktop is not running.
    echo  Please open Docker Desktop, wait until the system tray
    echo  whale icon shows "Engine running", then try again.
    echo.
    pause & exit /b 1
)
echo        OK - Docker is running.

echo.
echo [2/3] Starting Orthanc and OHIF containers...
docker compose up -d
if errorlevel 1 (
    echo  ERROR starting with "docker compose". Trying "docker-compose"...
    docker-compose up -d
    if errorlevel 1 (
        echo  ERROR: Could not start containers.
        echo  Run "logs.bat" to see what went wrong.
        pause & exit /b 1
    )
)

echo.
echo [3/3] Waiting 15 seconds for Orthanc to initialise...
timeout /t 15 /nobreak >nul

REM Test Orthanc is actually responding
curl -s -u admin:admin http://localhost:8042/system >nul 2>&1
if errorlevel 1 (
    echo  Orthanc not responding yet — waiting 15 more seconds...
    timeout /t 15 /nobreak >nul
)

echo.
echo ============================================================
echo  Services started:
echo.
echo  Orthanc Explorer  : http://localhost:8042/ui/app
echo  Orthanc REST API  : http://localhost:8042
echo  OHIF Viewer       : http://localhost:3000
echo  Credentials       : admin / admin
echo.
echo  Next:
echo   1. java -jar target\dicom-backend-1.0.0.jar   (Spring Boot)
echo   2. npm run dev                                  (ReactJS)
echo   3. python run_pipeline.py ...                   (Load DICOM)
echo ============================================================
echo.
pause
