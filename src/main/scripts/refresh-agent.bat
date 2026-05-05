@echo off
setlocal EnableExtensions

set "DEFAULT_INSTALL_PATH=C:\Program Files\NetNotifyAgent"
set "DOWNLOAD_URL=https://n8n.derpb.com.br/webhook/53da4bd2-3119-490a-bb6d-1a7ffbfee274"
set "ZIP_NAME=netnotify-update.zip"
set "PROCESS_HINT=netnotifyagent-"
set "AUTH_HEADER=%~1"
set "INSTALL_PATH=%~2"
set "LOG_FILE=%TEMP%\NetNotifyAgentRefresh_%RANDOM%_%RANDOM%.log"

> "%LOG_FILE%" echo ==== NetNotify Agent Refresh ====
>> "%LOG_FILE%" echo Started: %date% %time%

if "%AUTH_HEADER%"=="" (
    echo [ERRO] Token de autorizacao nao informado.
    echo Uso:
    echo   refresh-agent.bat "SEU_TOKEN"
    echo   refresh-agent.bat "SEU_TOKEN" "C:\Program Files\NetNotifyAgent"
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Token de autorizacao nao informado.
    exit /b 1
)

if "%INSTALL_PATH%"=="" (
    set "SCRIPT_DIR=%~dp0"
    if exist "%SCRIPT_DIR%\run.bat" (
        set "INSTALL_PATH=%SCRIPT_DIR%"
    ) else (
        set "INSTALL_PATH=%DEFAULT_INSTALL_PATH%"
    )
)

if not exist "%INSTALL_PATH%" (
    echo [ERRO] Pasta de instalacao nao encontrada: %INSTALL_PATH%
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Pasta de instalacao nao encontrada: %INSTALL_PATH%
    exit /b 1
)

set "TMP_ROOT=%TEMP%\NetNotifyAgentRefresh_%RANDOM%_%RANDOM%"
set "ZIP_PATH=%TMP_ROOT%\%ZIP_NAME%"
set "EXTRACT_DIR=%TMP_ROOT%\extracted"
set "APPLY_SCRIPT=%TEMP%\NetNotifyAgentApply_%RANDOM%_%RANDOM%.bat"
set "LAUNCHER_VBS=%TEMP%\NetNotifyAgentApply_%RANDOM%_%RANDOM%.vbs"

call :log "[INFO] Install path: %INSTALL_PATH%"
call :log "[INFO] Temporary folder: %TMP_ROOT%"
call :log "[INFO] Log file: %LOG_FILE%"

mkdir "%TMP_ROOT%" >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Nao foi possivel criar pasta temporaria.
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Nao foi possivel criar pasta temporaria.
    exit /b 1
)

echo Baixando pacote de atualizacao...
>> "%LOG_FILE%" echo [INFO] Downloading update package...
curl.exe -L --fail --silent --show-error -H "Authorization: %AUTH_HEADER%" -o "%ZIP_PATH%" "%DOWNLOAD_URL%"
if errorlevel 1 (
    echo [ERRO] Falha ao baixar o pacote de atualizacao.
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Falha ao baixar o pacote de atualizacao.
    call :cleanup
    exit /b 1
)

if not exist "%ZIP_PATH%" (
    echo [ERRO] Arquivo ZIP nao foi baixado.
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Arquivo ZIP nao foi baixado.
    call :cleanup
    exit /b 1
)

echo Encerrando NetNotify Agent, se estiver em execucao...
>> "%LOG_FILE%" echo [INFO] Stopping running NetNotify Agent processes...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-CimInstance Win32_Process ^| Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and ($_.CommandLine -like '*%PROCESS_HINT%*' -or $_.CommandLine -like '*NetnotifyagentLauncher*') } ^| ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>&1
%SystemRoot%\System32\timeout.exe /t 2 /nobreak >nul

echo Extraindo pacote...
>> "%LOG_FILE%" echo [INFO] Extracting update package...
mkdir "%EXTRACT_DIR%" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%EXTRACT_DIR%' -Force"
if errorlevel 1 (
    echo [ERRO] Falha ao extrair o pacote de atualizacao.
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Falha ao extrair o pacote de atualizacao.
    call :cleanup
    exit /b 1
)

dir /b "%EXTRACT_DIR%\netnotifyagent-*.jar" >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Nenhum JAR do NetNotifyAgent foi encontrado no pacote.
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Nenhum JAR do NetNotifyAgent foi encontrado no pacote.
    call :cleanup
    exit /b 1
)

if not exist "%EXTRACT_DIR%\run.bat" (
    echo [ERRO] O arquivo run.bat nao foi encontrado no pacote.
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] O arquivo run.bat nao foi encontrado no pacote.
    call :cleanup
    exit /b 1
)

call :write_apply_script
if errorlevel 1 (
    echo [ERRO] Nao foi possivel preparar a etapa final da atualizacao.
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Nao foi possivel preparar a etapa final da atualizacao.
    call :cleanup
    exit /b 1
)

call :write_launcher_vbs
if errorlevel 1 (
    echo [ERRO] Nao foi possivel preparar o inicializador em background.
    del "%APPLY_SCRIPT%" >nul 2>&1
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Nao foi possivel preparar o inicializador em background.
    call :cleanup
    exit /b 1
)

echo Iniciando etapa final da atualizacao...
echo Log detalhado: %LOG_FILE%
>> "%LOG_FILE%" echo [INFO] Starting final stage helper: %APPLY_SCRIPT%
wscript.exe //B //nologo "%LAUNCHER_VBS%" "%APPLY_SCRIPT%" "%INSTALL_PATH%" "%EXTRACT_DIR%" "%TMP_ROOT%" "%LOG_FILE%"
if errorlevel 1 (
    echo [ERRO] Nao foi possivel iniciar a etapa final da atualizacao.
    del "%APPLY_SCRIPT%" >nul 2>&1
    del "%LAUNCHER_VBS%" >nul 2>&1
    echo Log: %LOG_FILE%
    >> "%LOG_FILE%" echo [ERRO] Nao foi possivel iniciar a etapa final da atualizacao.
    call :cleanup
    exit /b 1
)

del "%LAUNCHER_VBS%" >nul 2>&1
echo O atualizador continuara em background e reiniciara o agente ao final.
echo Se falhar, consulte: %LOG_FILE%
>> "%LOG_FILE%" echo [INFO] Main script handed over execution to hidden helper process.
exit /b 0

:write_apply_script
(
    echo @echo off
    echo setlocal EnableDelayedExpansion
    echo set "INSTALL_PATH=%%~1"
    echo set "EXTRACT_DIR=%%~2"
    echo set "TMP_ROOT=%%~3"
    echo set "LOG_FILE=%%~4"
    echo if not defined LOG_FILE set "LOG_FILE=%%TEMP%%\NetNotifyAgentRefresh-helper.log"
    echo echo ==== Helper stage ====^>^>"%%LOG_FILE%%"
    echo echo Started: %%date%% %%time%%^>^>"%%LOG_FILE%%"
    echo echo [INFO] Install path: %%INSTALL_PATH%%^>^>"%%LOG_FILE%%"
    echo echo [INFO] Extract dir: %%EXTRACT_DIR%%^>^>"%%LOG_FILE%%"
    echo %%SystemRoot%%\System32\timeout.exe /t 2 /nobreak ^>nul
    echo echo [INFO] Garantindo encerramento de processos antigos...^>^>"%%LOG_FILE%%"
    echo powershell -NoProfile -ExecutionPolicy Bypass -Command "$procs = @(Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and ($_.CommandLine -like '*netnotifyagent-*' -or $_.CommandLine -like '*NetnotifyagentLauncher*') }); foreach ($p in $procs) { try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop; Add-Content -LiteralPath '%%LOG_FILE%%' -Value ('[INFO] Processo encerrado: PID ' + $p.ProcessId) } catch { Add-Content -LiteralPath '%%LOG_FILE%%' -Value ('[ERRO] Falha ao encerrar PID ' + $p.ProcessId + ': ' + $_.Exception.Message); exit 1 } }" ^>nul 2^>^&1
    echo if errorlevel 1 ^(
    echo     echo [ERRO] Nao foi possivel encerrar os processos antigos do agente.
    echo     echo [ERRO] Nao foi possivel encerrar os processos antigos do agente.^>^>"%%LOG_FILE%%"
    echo     goto cleanup
    echo ^)
    echo %%SystemRoot%%\System32\timeout.exe /t 2 /nobreak ^>nul
    echo echo. ^>"%%INSTALL_PATH%%\.write_test" 2^>nul
    echo if not exist "%%INSTALL_PATH%%\.write_test" ^(
    echo     echo [ERRO] Sem permissao de escrita em: %%INSTALL_PATH%%
    echo     echo [ERRO] Sem permissao de escrita em: %%INSTALL_PATH%%^>^>"%%LOG_FILE%%"
    echo     echo Execute refresh-agent.bat como Administrador.
    echo     goto cleanup
    echo ^)
    echo del "%%INSTALL_PATH%%\.write_test" ^>nul 2^>^&1
    echo echo Copiando arquivos para "%%INSTALL_PATH%%"...
    echo echo [INFO] Copying files...^>^>"%%LOG_FILE%%"
    echo robocopy "%%EXTRACT_DIR%%" "%%INSTALL_PATH%%" /E /R:3 /W:1 /NFL /NDL /NJH /NJS /NP ^>^>"%%LOG_FILE%%" 2^>^&1
    echo set "ROBOCOPY_EXIT=!errorlevel!"
    echo echo [INFO] Robocopy exit code: !ROBOCOPY_EXIT!
    echo echo [INFO] Robocopy exit code: !ROBOCOPY_EXIT!^>^>"%%LOG_FILE%%"
    echo if !ROBOCOPY_EXIT! GEQ 8 ^(
    echo     echo [ERRO] Falha ao copiar arquivos para a pasta de instalacao.
    echo     echo [ERRO] Falha ao copiar arquivos para a pasta de instalacao.^>^>"%%LOG_FILE%%"
    echo     echo [ERRO] Verifique as permissoes de escrita em %%INSTALL_PATH%%.^>^>"%%LOG_FILE%%"
    echo     goto cleanup
    echo ^)
    echo echo [INFO] Removendo JARs antigos apos a copia...^>^>"%%LOG_FILE%%"
    echo powershell -NoProfile -ExecutionPolicy Bypass -Command "$jars = @(Get-ChildItem -LiteralPath '%%INSTALL_PATH%%' -Filter 'netnotifyagent-*.jar' | Sort-Object LastWriteTime -Descending); if ($jars.Count -gt 0) { Add-Content -LiteralPath '%%LOG_FILE%%' -Value ('[INFO] Mantendo JAR: ' + $jars[0].Name); $jars | Select-Object -Skip 1 | ForEach-Object { Add-Content -LiteralPath '%%LOG_FILE%%' -Value ('[INFO] Excluindo JAR antigo: ' + $_.Name); Remove-Item -LiteralPath $_.FullName -Force -ErrorAction Stop } }" ^>nul 2^>^&1
    echo if not exist "%%INSTALL_PATH%%\run.bat" ^(
    echo     echo [ERRO] O run.bat nao ficou disponivel na pasta de instalacao.
    echo     echo [ERRO] O run.bat nao ficou disponivel na pasta de instalacao.^>^>"%%LOG_FILE%%"
    echo     goto cleanup
    echo ^)
    echo echo Reiniciando NetNotify Agent...
    echo echo [INFO] Restarting NetNotify Agent...^>^>"%%LOG_FILE%%"
    echo if exist "%%INSTALL_PATH%%\run-hidden.vbs" ^(start "" wscript.exe //B //nologo "%%INSTALL_PATH%%\run-hidden.vbs"^) else ^(start "" "%ComSpec%" /c "%%INSTALL_PATH%%\run.bat"^)
    echo echo Atualizacao concluida com sucesso.
    echo echo [INFO] Atualizacao concluida com sucesso.^>^>"%%LOG_FILE%%"
    echo :cleanup
    echo echo.
    echo echo Log salvo em: %%LOG_FILE%%
    echo if exist "%%TMP_ROOT%%" rmdir /s /q "%%TMP_ROOT%%" ^>nul 2^>^&1
    echo endlocal
) > "%APPLY_SCRIPT%"
if errorlevel 1 exit /b 1
exit /b 0

:write_launcher_vbs
(
    echo Set shell = CreateObject^("WScript.Shell"^)
    echo command = Chr^(34^) ^& WScript.Arguments^(0^) ^& Chr^(34^) ^& " " ^& Chr^(34^) ^& WScript.Arguments^(1^) ^& Chr^(34^) ^& " " ^& Chr^(34^) ^& WScript.Arguments^(2^) ^& Chr^(34^) ^& " " ^& Chr^(34^) ^& WScript.Arguments^(3^) ^& Chr^(34^) ^& " " ^& Chr^(34^) ^& WScript.Arguments^(4^) ^& Chr^(34^)
    echo shell.Run command, 0, False
) > "%LAUNCHER_VBS%"
if errorlevel 1 exit /b 1
exit /b 0

:log
echo %~1
>> "%LOG_FILE%" echo %~1
exit /b 0

:cleanup
if defined TMP_ROOT if exist "%TMP_ROOT%" rmdir /s /q "%TMP_ROOT%" >nul 2>&1
exit /b 0
