@echo off
REM Script de pós-instalação para aplicar permissões na pasta resources
setlocal enabledelayedexpansion

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo Este script requer privilegios de administrador.
    echo Reabrindo com privilegios elevados...
    echo.
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%0\" %*' -Verb RunAs" >nul 2>&1
    exit /b 0
)

set "INSTALL_PATH=%~1"
if "%INSTALL_PATH%"=="" (
    set "SCRIPT_DIR=%~dp0"
    if "!SCRIPT_DIR:~-1!"=="\" set "SCRIPT_DIR=!SCRIPT_DIR:~0,-1!"
    set "INSTALL_PATH=!SCRIPT_DIR!"
    set "SETTINGS_FILE=!INSTALL_PATH!\resources\settings.properties"
    if exist "!SETTINGS_FILE!" (
        for /f "usebackq eol=# tokens=1,* delims==" %%A in ("!SETTINGS_FILE!") do (
            if /I "%%~A"=="install.path" (
                set "VALUE=%%~B"
                if defined VALUE (
                    set "VALUE=!VALUE:\"=!"
                    set "VALUE=!VALUE:/=\!"
                    if not "!VALUE!"=="" (
                        set "INSTALL_PATH=!VALUE!"
                    )
                )
            )
        )
    )
)

if "!INSTALL_PATH:~-1!"=="\" set "INSTALL_PATH=!INSTALL_PATH:~0,-1!"

if "!INSTALL_PATH!"=="" (
    echo Erro: Caminho da instalacao nao informado.
    exit /b 1
)

set "RESOURCES_PATH=!INSTALL_PATH!\resources"

echo Configurando permissoes para a pasta resources...
if exist "!RESOURCES_PATH!" (
    icacls "!RESOURCES_PATH!" /grant "*S-1-5-32-545:(OI)(CI)F" /T /C
    if !errorlevel! equ 0 (
        echo OK: Permissoes aplicadas.
    ) else (
        echo Aviso: Falha ao aplicar permissoes.
    )
) else (
    echo Aviso: Pasta de resources nao encontrada: !RESOURCES_PATH!
)

exit /b 0
