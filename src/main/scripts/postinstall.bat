@echo off
REM Script de pós-instalação para definir permissões de escrita na pasta de resources
REM Este script concede permissões de escrita para todos os usuários na pasta de resources

setlocal enabledelayedexpansion

REM Verifica se o script está sendo executado com privilégios de administrador
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Erro: Este script requer privilégios de administrador.
    exit /b 1
)

REM Obtém o caminho da pasta de instalação (assumindo que é passado como argumento)
if "%~1"=="" (
    echo Erro: Caminho da instalação não fornecido.
    exit /b 1
)

set INSTALL_PATH=%~1
set RESOURCES_PATH=%INSTALL_PATH%\resources

REM Verifica se a pasta de resources existe
if not exist "%RESOURCES_PATH%" (
    echo Erro: Pasta de resources não encontrada em %RESOURCES_PATH%
    exit /b 1
)

echo Configurando permissões de escrita para a pasta de resources...
echo Caminho: %RESOURCES_PATH%

REM Concede permissões de escrita para todos os usuários (grupo "Users")
icacls "%RESOURCES_PATH%" /grant "Users:(OI)(CI)M" /T /C

if %errorlevel% equ 0 (
    echo Permissões de escrita concedidas com sucesso para a pasta de resources.
    exit /b 0
) else (
    echo Erro ao configurar as permissões. Código de erro: %errorlevel%
    exit /b 1
)
