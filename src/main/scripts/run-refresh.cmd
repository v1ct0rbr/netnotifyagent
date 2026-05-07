@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "REFRESH_SCRIPT=%SCRIPT_DIR%\refresh-agent.bat"
set "LOG_DIR=%ProgramData%\NetNotifyAgent"
set "LAUNCH_LOG=%LOG_DIR%\run-refresh-launcher.log"
set "LAUNCHER_VBS=%TEMP%\NetNotifyRunRefresh_%RANDOM%_%RANDOM%.vbs"

REM Wrapper para o ESET: dispara o refresh em background e retorna rapidamente.

if not exist "%REFRESH_SCRIPT%" (
    echo [ERRO] Arquivo refresh-agent.bat nao encontrado em "%SCRIPT_DIR%"
    endlocal & exit /b 1
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

> "%LAUNCH_LOG%" echo ==== NetNotify Run Refresh Launcher ====
>> "%LAUNCH_LOG%" echo Started: %date% %time%
>> "%LAUNCH_LOG%" echo Script dir: %SCRIPT_DIR%
>> "%LAUNCH_LOG%" echo Refresh script: %REFRESH_SCRIPT%

(
    echo Set shell = CreateObject^("WScript.Shell"^)
    echo command = Chr^(34^) ^& WScript.Arguments^(0^) ^& Chr^(34^)
    echo For i = 1 To WScript.Arguments.Count - 1
    echo   command = command ^& " " ^& Chr^(34^) ^& Replace^(WScript.Arguments^(i^), Chr^(34^), Chr^(34^) ^& Chr^(34^)^) ^& Chr^(34^)
    echo Next
    echo shell.Run command, 0, False
) > "%LAUNCHER_VBS%"

if errorlevel 1 (
    echo [ERRO] Nao foi possivel preparar o inicializador do refresh.
    >> "%LAUNCH_LOG%" echo [ERRO] Falha ao criar launcher VBS.
    endlocal & exit /b 1
)

wscript.exe //B //nologo "%LAUNCHER_VBS%" "%REFRESH_SCRIPT%" %*
set "EXITCODE=%ERRORLEVEL%"
del "%LAUNCHER_VBS%" >nul 2>&1

if not "%EXITCODE%"=="0" (
    echo [ERRO] Nao foi possivel iniciar o refresh em background.
    >> "%LAUNCH_LOG%" echo [ERRO] Falha ao iniciar refresh-agent.bat. Exit code: %EXITCODE%
    endlocal & exit /b %EXITCODE%
)

>> "%LAUNCH_LOG%" echo [INFO] Refresh disparado em background.
echo Refresh disparado em background.
echo Log do launcher: %LAUNCH_LOG%
endlocal & exit /b 0
