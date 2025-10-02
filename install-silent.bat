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

echo Debug: ORIGINAL_DIR=%ORIGINAL_DIR%
echo Debug: ORIGINAL_DIR_NO_SLASH=%ORIGINAL_DIR_NO_SLASH%
echo Debug: SCRIPT_PATH=%SCRIPT_PATH%
echo Debug: ARGS=%*
timeout /t 1 /nobreak >nul

rem ---------- localizar instalador (auto se nao informado) ----------
if "%INSTALLER%"=="" (
    for %%F in (NetNotifyAgent-Setup-*.exe) do if not defined INSTALLER set "INSTALLER=%%~fF"
    if "%INSTALLER%"=="" (
        echo ERRO: instalador nao informado e nenhum NetNotifyAgent-Setup-*.exe encontrado.
        echo Debug: conteudo da pasta:
        dir /b
        timeout /t 3 /nobreak >nul
        exit /b 1
    ) else (
        echo Instalador detectado automaticamente: %INSTALLER%
    )
)

if not exist "%INSTALLER%" (
    echo ERRO: instalador "%INSTALLER%" nao encontrado.
    echo Debug: arquivo procurado: %INSTALLER%
    timeout /t 3 /nobreak >nul
    exit /b 1
)

rem ---------- executar instalador (silencioso) ----------
echo Iniciando instalador: "%INSTALLER%"
echo Debug: inicio instalador em %DATE% %TIME%
"%INSTALLER%" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS="createscheduledtask"
set "INSTALLER_RC=%errorlevel%"
echo Debug: retorno do instalador: %INSTALLER_RC%
echo Pausando 3s para escrita de arquivos e criação da task...
timeout /t 3 /nobreak >nul

echo Instalacao concluida. Verificando...

rem ---------- verificar se arquivos foram instalados (usa existencia, nao codigo de retorno) ----------
set "INSTALL_PATH="
if exist "C:\Program Files\NetNotifyAgent\netnotifyagent-1.0-SNAPSHOT.jar" (
    set "INSTALL_PATH=C:\Program Files\NetNotifyAgent"
) else if exist "C:\Program Files (x86)\NetNotifyAgent\netnotifyagent-1.0-SNAPSHOT.jar" (
    set "INSTALL_PATH=C:\Program Files (x86)\NetNotifyAgent"
)

echo Instalado em: "%INSTALL_PATH%"
timeout /t 3 /nobreak >nul



echo Verificado: arquivos instalados em %INSTALL_PATH%.
timeout /t 3 /nobreak >nul

rem ---------- (re)criar task via script create-scheduled-task.ps1 para garantir configuração correta ----------
if exist "%INSTALL_PATH%\create-scheduled-task.ps1" (
    echo Executando create-scheduled-task.ps1 para garantir a task...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%INSTALL_PATH%\create-scheduled-task.ps1" -TaskName "NetNotifyAgent" -BaseDir "%INSTALL_PATH%"
    set "PS_RC_CREATE=%errorlevel%"
    echo Debug: retorno do script de criacao da task: %PS_RC_CREATE%
) else (
    echo Aviso: create-scheduled-task.ps1 nao encontrado em "%INSTALL_PATH%".
    echo Continuando sem recriar a task.
)
timeout /t 3 /nobreak >nul

echo Verificando se a Scheduled Task existe (scholastic check)...
schtasks /query /tn "NetNotifyAgent" /fo LIST 2>nul
if %errorlevel% neq 0 (
    echo Aviso: schtasks nao encontrou a task "NetNotifyAgent" (codigo %errorlevel%).
) else (
    echo Task encontrada via schtasks.
)
echo Também consultando Get-ScheduledTask (PowerShell)...
powershell -NoProfile -Command "try { Get-ScheduledTask -TaskName 'NetNotifyAgent' | Select-Object TaskName,State } catch { Write-Host 'Get-ScheduledTask nao encontrou a task' }"
timeout /t 3 /nobreak >nul

rem ---------- tentar iniciar a Scheduled Task com retries ----------
set "TASK_STARTED=0"
set /a TRY=0
:TRY_START
if %TRY% geq 3 goto START_FAILED
set /a TRY+=1
echo Tentativa de start #%TRY% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { Start-ScheduledTask -TaskName 'NetNotifyAgent' -ErrorAction Stop; Write-Host 'Start-ScheduledTask -> OK' ; exit 0 } catch { Write-Host 'Start-ScheduledTask -> FALHA: ' $_.Exception.Message; exit 1 }"
set "PS_RC=%errorlevel%"
echo Debug: PS_RC apos Start-ScheduledTask = %PS_RC%
timeout /t 3 /nobreak >nul

if "%PS_RC%"=="0" (
    set "TASK_STARTED=1"
    goto START_CHECK
) else (
    echo Aguarda 3s antes de nova tentativa...
    timeout /t 3 /nobreak >nul
    goto TRY_START
)

:START_FAILED
echo Falha ao iniciar a Scheduled Task apos 3 tentativas.
rem continuar para fallback

:START_CHECK
echo Verificando se o NetNotify Agent iniciou via task...
timeout /t 3 /nobreak >nul

if "%TASK_STARTED%"=="1" (
    rem verificar se processo javaw (netnotifyagent) apareceu
    tasklist /fi "imagename eq javaw.exe" 2>nul | find /i "javaw.exe" >nul
    if %errorlevel% equ 0 (
        echo NetNotify Agent iniciado via Scheduled Task (javaw.exe detectado).
        echo Listando processos javaw com linha de comando (filtro netnotifyagent)...
        powershell -NoProfile -Command "Get-WmiObject Win32_Process -Filter \"Name='javaw.exe'\" | Select-Object ProcessId, CommandLine | Where-Object { \$_.CommandLine -like '*netnotifyagent*' } | Format-List"
        timeout /t 3 /nobreak >nul
        echo Instalacao e inicializacao OK.
        exit /b 0
    ) else (
        echo Scheduled Task disparada, mas processo nao detectado. Iremos tentar fallback.
    )
) else (
    echo Task nao iniciada com sucesso (TASK_STARTED=%TASK_STARTED%).
)

rem ---------- fallback: executar run.bat manualmente ----------
if exist "%INSTALL_PATH%\run.bat" (
    echo Tentando iniciar run.bat manualmente (modo teste)...
    pushd "%INSTALL_PATH%"
    call "run.bat"
    set "BAT_RESULT=%errorlevel%"
    echo Debug: retorno do teste run.bat = %BAT_RESULT%
    popd
    timeout /t 3 /nobreak >nul

    if "%BAT_RESULT%"=="0" (
        echo Iniciando run.bat em background...
        start "" "%INSTALL_PATH%\run.bat"
        timeout /t 3 /nobreak >nul
        tasklist /fi "imagename eq javaw.exe" 2>nul | find /i "javaw.exe" >nul
        if %errorlevel% equ 0 (
            echo NetNotify Agent iniciado via run.bat.
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

rem fallback final
echo Erro final: nao foi possivel iniciar NetNotify Agent automaticamente.
echo Verifique manualmente: cd "%INSTALL_PATH%" && run.bat
timeout /t 5 /nobreak >nul
exit /b 10