@echo off
title CuotaJusta - Frontend
cd /d "%~dp0"
if not exist node_modules (
  echo Instalando dependencias por primera vez...
  call npm.cmd install
)
echo Iniciando el frontend de CuotaJusta en http://localhost:5173
echo Para apagarlo: Ctrl + C
call npm.cmd run dev
pause
