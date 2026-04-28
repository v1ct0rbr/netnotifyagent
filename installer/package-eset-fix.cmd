@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "OUTPUT_DIR=%SCRIPT_DIR%\eset-fix-package"

if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%OUTPUT_DIR%"

copy /y "%SCRIPT_DIR%\fix-java-home.bat" "%OUTPUT_DIR%\" >nul
copy /y "%SCRIPT_DIR%\run.bat.template" "%OUTPUT_DIR%\" >nul
copy /y "%SCRIPT_DIR%\run-hidden.vbs.template" "%OUTPUT_DIR%\" >nul
copy /y "%SCRIPT_DIR%\execute-fix.cmd" "%OUTPUT_DIR%\" >nul
copy /y "%SCRIPT_DIR%\README-ESET.md" "%OUTPUT_DIR%\" >nul

echo Pacote gerado em: %OUTPUT_DIR%
exit /b 0
