@echo off
title CuotaJusta - Backend
cd /d "%~dp0"
rem Usa el Java 21 que descargo IntelliJ si Windows no tiene JAVA_HOME configurado
if not defined JAVA_HOME set "JAVA_HOME=C:\Users\J DIEGO\.jdks\ms-21.0.12.1"
echo Iniciando el backend de CuotaJusta (puerto 8080)...
echo Esta listo cuando veas "Started BackendApplication". Para apagarlo: Ctrl + C
call "%~dp0mvnw.cmd" spring-boot:run
pause
