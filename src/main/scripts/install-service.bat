@echo off
REM Script de instalacao do servico NetNotify Agent
REM Este script chama a versao PowerShell que e mais confiavel
REM Parametros:
REM   %1 = Caminho de instalacao (obrigatorio)
REM   %2 = Caminho do Java (opcional)

setlocal EnableDelayedExpansion

REM Obter parametros
set "INSTALL_PATH=%~1"
if "!INSTALL_PATH!"=="" (
    set "INSTALL_PATH=%cd%"
)

set "PROVIDED_JAVA=%~2"

REM Chamar o script PowerShell
echo Iniciando instalacao do servico...
echo.

if "!PROVIDED_JAVA!"=="" (
    powershell -ExecutionPolicy Bypass -File "!INSTALL_PATH!\install-service.ps1" "!INSTALL_PATH!"
) else (
    powershell -ExecutionPolicy Bypass -File "!INSTALL_PATH!\install-service.ps1" "!INSTALL_PATH!" "!PROVIDED_JAVA!"
)

exit /b !errorlevel!
