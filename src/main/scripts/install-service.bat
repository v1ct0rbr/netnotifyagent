@echo off
REM Script de instalacao do servico NetNotify Agent
REM Este script chama a versao PowerShell que e mais confiavel
REM Parametros:
REM   %1 = Caminho de instalacao (obrigatorio)
REM   %2 = Caminho do Java (opcional)
REM   %3 = /pause (opcional - mantem a janela aberta ao final)

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
set "PAUSE_FLAG=%~3"

REM Chamar o script PowerShell
echo Iniciando instalacao do servico...
echo.

if "!PROVIDED_JAVA!"=="" (
    if /I "!PAUSE_FLAG!"=="/pause" (
        powershell -ExecutionPolicy Bypass -File "!SCRIPT_DIR!\install-service.ps1" "!INSTALL_PATH!" -Pause
    ) else (
        powershell -ExecutionPolicy Bypass -File "!SCRIPT_DIR!\install-service.ps1" "!INSTALL_PATH!"
    )
) else (
    if /I "!PAUSE_FLAG!"=="/pause" (
        powershell -ExecutionPolicy Bypass -File "!SCRIPT_DIR!\install-service.ps1" "!INSTALL_PATH!" "!PROVIDED_JAVA!" -Pause
    ) else (
        powershell -ExecutionPolicy Bypass -File "!SCRIPT_DIR!\install-service.ps1" "!INSTALL_PATH!" "!PROVIDED_JAVA!"
    )
)

set "EXITCODE=!errorlevel!"

if /I "!PAUSE_FLAG!"=="/pause" (
    echo.
    echo Execucao concluida com codigo !EXITCODE!.
    pause
)

exit /b !EXITCODE!
