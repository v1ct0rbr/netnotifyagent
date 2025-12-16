@echo off
REM Script de instalacao do servico NetNotify Agent
REM Este script chama a versao PowerShell que e mais confiavel
REM Parametros:
REM   %1 = Caminho de instalacao (obrigatorio)
REM   %2 = Caminho do Java (opcional)

setlocal EnableDelayedExpansion

REM Ajustar diretório do script (sem barra final)
set "SCRIPT_DIR=%~dp0"
if defined SCRIPT_DIR if "!SCRIPT_DIR:~-1!"=="\" set "SCRIPT_DIR=!SCRIPT_DIR:~0,-1!"

REM Obter parametros
set "INSTALL_PATH=%~1"
if "!INSTALL_PATH!"=="" (
    set "INSTALL_PATH=!SCRIPT_DIR!"
)

set "PROVIDED_JAVA=%~2"

REM Chamar o script PowerShell
echo Iniciando instalacao do servico...
echo.

if "!PROVIDED_JAVA!"=="" (
    powershell -ExecutionPolicy Bypass -File "!SCRIPT_DIR!\install-service.ps1" "!INSTALL_PATH!"
) else (
    powershell -ExecutionPolicy Bypass -File "!SCRIPT_DIR!\install-service.ps1" "!INSTALL_PATH!" "!PROVIDED_JAVA!"
)

exit /b !errorlevel!
