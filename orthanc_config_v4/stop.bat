@echo off
echo Stopping QuantixMed containers...
docker compose down 2>nul || docker-compose down 2>nul
echo Stopped.
pause
