@echo off
title CuotaJusta
cd /d "%~dp0"
echo Abriendo el backend en otra ventana...
start "CuotaJusta - Backend" "%~dp0backend\iniciar-backend.cmd"

echo Esperando a que el backend arranque (puede tardar hasta 1 minuto)...
:esperar
timeout /t 3 /nobreak >nul
curl -s -o nul http://localhost:8080/api/health || goto esperar

echo Backend listo. Abriendo el frontend...
start "CuotaJusta - Frontend" "%~dp0frontend\iniciar-frontend.cmd"
timeout /t 5 /nobreak >nul
start "" http://localhost:5173
echo Listo. Puedes cerrar esta ventana. Para apagar todo, cierra las ventanas Backend y Frontend.
timeout /t 10 >nul
