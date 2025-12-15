@echo off
REM Script de pós-instalação para definir permissões de escrita na pasta de resources
REM Este script é executado automaticamente pelo instalador com privilégios de admin

setlocal enabledelayedexpansion

REM Obtém o caminho da pasta de instalação (assumindo que é passado como argumento)
if "%~1"=="" (
    echo Erro: Caminho da instalação não fornecido.
    exit /b 1
)

set INSTALL_PATH=%~1
set RESOURCES_PATH=%INSTALL_PATH%\resources

echo Configurando permissoes para a pasta de resources...
echo Caminho: %RESOURCES_PATH%

REM Concede permissões de escrita - compatível com UAC
if exist "%RESOURCES_PATH%" (
    icacls "%RESOURCES_PATH%" /grant *S-1-1-0:(OI)(CI)M /T /C >nul 2>&1
    echo OK: Permissoes concedidas.
)

exit /b 0
