@echo off
setlocal EnableDelayedExpansion

rem ---------- variaveis ----------
set "ORIGINAL_DIR=%~dp0"
rem remover barra final para evitar problemas com aspas
set "ORIGINAL_DIR_NO_SLASH=%ORIGINAL_DIR:~0,-1%"
set "SCRIPT_PATH=%~f0"
set "INSTALLER=%~1"

rem ---------- elevacao (reexecuta sem interacao) ----------
net session >nul 2>&1
if %errorlevel% neq 0 (
    rem relanca o script elevado e sai (sem pausa/interacao)
    powershell -NoProfile -Command "Start-Process -FilePath 'cmd.exe' -ArgumentList '/c cd /d \"%ORIGINAL_DIR_NO_SLASH%\" && \"%SCRIPT_PATH%\" %*' -Verb RunAs"
    exit /b 0
)

rem ---------- executar como administrador ----------
cd /d "%ORIGINAL_DIR_NO_SLASH%"

rem ---------- localizar instalador (auto se nao informado) ----------
if "%INSTALLER%"=="" (
    for %%F in (NetNotifyAgent-Setup-*.exe) do if not defined INSTALLER set "INSTALLER=%%~fF"
    if "%INSTALLER%"=="" (
        echo ERRO: instalador nao informado e nenhum NetNotifyAgent-Setup-*.exe encontrado.
        exit /b 1
    )
)

if not exist "%INSTALLER%" (
    echo ERRO: instalador "%INSTALLER%" nao encontrado.
    exit /b 1
)

rem ---------- executar instalador (silencioso) ----------
echo Iniciando instalador: "%INSTALLER%"
"%INSTALLER%" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS="createscheduledtask"

rem aguardar pausadamente para escrita de arquivos / criação de task
timeout /t 3 /nobreak >nul

rem ---------- verificar se arquivos foram instalados ----------
set "INSTALL_PATH="
if exist "C:\Program Files\NetNotifyAgent\netnotifyagent-1.0-SNAPSHOT.jar" (
    set "INSTALL_PATH=C:\Program Files\NetNotifyAgent"
) else if exist "C:\Program Files (x86)\NetNotifyAgent\netnotifyagent-1.0-SNAPSHOT.jar" (
    set "INSTALL_PATH=C:\Program Files (x86)\NetNotifyAgent"
)

if not defined INSTALL_PATH (
    echo ERRO: nao foi possivel localizar arquivos instalados.
    exit /b 2
)

rem ---------- garantir que a Scheduled Task exista ----------
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$t = Get-ScheduledTask -TaskName 'NetNotifyAgent' -ErrorAction SilentlyContinue; if (-not $t) { try { & '%INSTALL_PATH%\create-scheduled-task.ps1' -TaskName 'NetNotifyAgent' -BaseDir '%INSTALL_PATH%'; } catch { exit 3 } }"

rem aguardar consolidacao
timeout /t 3 /nobreak >nul

rem ---------- tentar iniciar a Scheduled Task ----------
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { Start-ScheduledTask -TaskName 'NetNotifyAgent' -ErrorAction Stop } catch { exit 4 }"

rem aguardar inicializacao do processo
timeout /t 3 /nobreak >nul

rem ---------- verificar se processo javaw.exe esta rodando ----------
tasklist /fi "imagename eq javaw.exe" 2>nul | find /i "javaw.exe" >nul
if %errorlevel% equ 0 (
    rem existe javaw.exe - verificar se e NetNotifyAgent
    powershell -NoProfile -Command "Get-WmiObject Win32_Process -Filter \"Name='javaw.exe'\" | Select-Object ProcessId, CommandLine | Where-Object { \$_.CommandLine -like '*netnotifyagent*' } | Format-List"
    exit /b 0
)

rem ---------- fallback: executar run.bat manualmente ----------
if exist "%INSTALL_PATH%\run.bat" (
    rem testar execução (saidas aparecerão no console)
    pushd "%INSTALL_PATH%"
    call "run.bat"
    set "BAT_RESULT=%errorlevel%"
    popd

    if %BAT_RESULT% equ 0 (
        rem iniciar em segundo plano
        start "" "%INSTALL_PATH%\run.bat"
        timeout /t 3 /nobreak >nul

        tasklist /fi "imagename eq javaw.exe" 2>nul | find /i "javaw.exe" >nul
        if %errorlevel% equ 0 (
            exit /b 0
        ) else (
            echo ERRO: processo nao apareceu apos iniciar run.bat.
            exit /b 5
        )
    ) else (
        echo ERRO: run.bat falhou no teste (codigo %BAT_RESULT%).
        exit /b 6
    )
) else (
    echo ERRO: run.bat nao encontrado em %INSTALL_PATH%.
    exit /b 7
)