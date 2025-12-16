@echo off
REM Wrapper para acionar o script PowerShell de desinstalação
setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
if defined SCRIPT_DIR if "!SCRIPT_DIR:~-1!"=="\" set "SCRIPT_DIR=!SCRIPT_DIR:~0,-1!"

set "SERVICE_NAME=%~1"
if "!SERVICE_NAME!"=="" set "SERVICE_NAME=NetNotifyAgent"

powershell -ExecutionPolicy Bypass -File "!SCRIPT_DIR!\uninstall-service.ps1" "!SERVICE_NAME!"
exit /b !errorlevel!
