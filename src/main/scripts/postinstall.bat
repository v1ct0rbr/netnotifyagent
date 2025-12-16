@echo off
REM Script de pós-instalação para definir permissões de escrita na pasta de resources
REM Este script é executado automaticamente pelo instalador com privilégios de admin

setlocal enabledelayedexpansion

REM Verifica se esta executando como administrador
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo Erro: Este script requer privilegios de administrador.
    echo Reabrindo com privilégios elevados...
    echo.
    
    REM Tentar elevar privilégios usando PowerShell
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%0\" %*' -Verb RunAs" >nul 2>&1
    if !errorlevel! equ 0 (
        exit /b 0
    ) else (
        echo.
        echo Falha ao elevar privilégios. Por favor, execute este script como administrador.
        pause
        exit /b 1
    )
)

REM Obtém o caminho da pasta de instalação (assumindo que é passado como argumento)
if "%~1"=="" (
    REM Se não foi passado, determina o diretório do script
    set "SCRIPT_DIR=%~dp0"
    
    REM Se %~dp0 está vazio, usar %cd% (diretório atual)
    if "!SCRIPT_DIR!"=="" (
        set "SCRIPT_DIR=%cd%"
    )
    
    REM Garantir que termina com barra
    if not "!SCRIPT_DIR:~-1!"=="\" set "SCRIPT_DIR=!SCRIPT_DIR!\"
    
    set "SETTINGS_FILE=!SCRIPT_DIR!resources\settings.properties"
    
    echo Debug: SCRIPT_DIR=!SCRIPT_DIR!
    echo Debug: Procurando: !SETTINGS_FILE!
    
    if exist "!SETTINGS_FILE!" (
        echo Lendo configuracao de install.path em !SETTINGS_FILE!...
        for /f "usebackq eol=# tokens=1,* delims==" %%A in ("!SETTINGS_FILE!") do (
            set "KEY=%%~A"
            set "VAL=%%~B"
            if /I "!KEY!"=="install.path" (
                if not "!VAL!"=="" (
                    set "INSTALL_PATH=!VAL!"
                    echo Encontrado install.path: !INSTALL_PATH!
                )
            )
        )
    ) else (
        echo Arquivo nao encontrado: !SETTINGS_FILE!
    )
    
    REM Se ainda não tem INSTALL_PATH, usar o diretório atual como fallback
    if "!INSTALL_PATH!"=="" (
        set "INSTALL_PATH=!SCRIPT_DIR!"
        REM Remove barra final para o fallback
        if "!INSTALL_PATH:~-1!"=="\" set "INSTALL_PATH=!INSTALL_PATH:~0,-1!"
    )
) else (
    REM Usar o parâmetro passado (prioridade máxima)
    set "INSTALL_PATH=%~1"
)

if "!INSTALL_PATH!"=="" (
    echo Erro: Caminho da instalacao nao fornecido e nao configurado em settings.properties.
    pause
    exit /b 1
)

set "RESOURCES_PATH=!INSTALL_PATH!\resources"

echo Configurando permissoes para a pasta de resources...
echo Caminho: !RESOURCES_PATH!

REM Concede permissões de escrita - compatível com UAC
REM S-1-5-32-545 é o SID do grupo Users local
if exist "!RESOURCES_PATH!" (
    icacls "!RESOURCES_PATH!" /grant "*S-1-5-32-545:(OI)(CI)F" /T /C
    if !errorlevel! equ 0 (
        echo OK: Permissoes concedidas com sucesso.
    ) else (
        echo Aviso: Houve um problema ao configurar permissoes.
    )
) else (
    echo Aviso: Pasta de resources nao encontrada: !RESOURCES_PATH!
)

exit /b 0
