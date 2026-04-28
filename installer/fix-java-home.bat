@echo off
setlocal EnableExtensions EnableDelayedExpansion
title NetNotify Agent - Reparo de Java e Inicializacao

echo ============================================================
echo  NetNotify Agent - Reparo de Java e launcher legado
echo ============================================================
echo.

set "SCRIPT_DIR=%~dp0"
if defined SCRIPT_DIR if "!SCRIPT_DIR:~-1!"=="\" set "SCRIPT_DIR=!SCRIPT_DIR:~0,-1!"

set "INSTALL_PATH="
set "SETTINGS_FILE="
set "RUN_BAT="
set "RUN_HIDDEN="
set "CURRENT_JAVA_HOME="
set "FOUND_JAVA_HOME="
set "RUN_REPAIRED=0"
set "SETTINGS_CHANGED=0"
set "TASKS_UPDATED=0"
set "SHOULD_PAUSE=0"

for %%A in (%*) do (
    if /I "%%~A"=="/pause" set "SHOULD_PAUSE=1"
)

call :resolve_install_path "%~1"
if not defined INSTALL_PATH call :detect_default_install

if not defined INSTALL_PATH (
    echo [ERRO] Instalacao do NetNotify Agent nao encontrada.
    echo.
    echo Uso:
    echo   fix-java-home.bat "C:\Program Files\NetNotifyAgent"
    echo   fix-java-home.bat "F:\Programas\run.bat"
    echo   fix-java-home.bat "F:\Programas" /pause
    goto :fail
)

set "SETTINGS_FILE=!INSTALL_PATH!\resources\settings.properties"
set "RUN_BAT=!INSTALL_PATH!\run.bat"
set "RUN_HIDDEN=!INSTALL_PATH!\run-hidden.vbs"

if not exist "!SETTINGS_FILE!" (
    echo [ERRO] settings.properties nao encontrado em:
    echo        !SETTINGS_FILE!
    goto :fail
)

echo Instalacao encontrada em: !INSTALL_PATH!
echo Arquivo de configuracao:  !SETTINGS_FILE!
echo Launcher a reparar:       !RUN_BAT!
echo.

call :read_current_java_home
call :resolve_java_home
if not defined FOUND_JAVA_HOME goto :fail

call :update_settings
if errorlevel 1 goto :fail

call :repair_legacy_launcher
if errorlevel 1 goto :fail

call :repair_hidden_launcher
if errorlevel 1 goto :fail

call :refresh_startup_tasks
if errorlevel 1 goto :fail

:success
echo.
echo ============================================================
echo  Reparo concluido.
if "!SETTINGS_CHANGED!"=="1" echo   - java.home atualizado para !FOUND_JAVA_HOME!
if "!RUN_REPAIRED!"=="1" echo   - run.bat legado substituido pelo launcher atual
if "!TASKS_UPDATED!"=="1" echo   - tasks de logon atualizadas para modo silencioso
if "!SETTINGS_CHANGED!!RUN_REPAIRED!!TASKS_UPDATED!"=="000" echo   - Nenhuma alteracao foi necessaria
echo  O agente deve iniciar corretamente no proximo logon.
echo ============================================================
echo.
if "!SHOULD_PAUSE!"=="1" pause
exit /b 0

:fail
echo.
echo ============================================================
echo  Reparo NAO concluido. Verifique os erros acima.
echo ============================================================
echo.
if "!SHOULD_PAUSE!"=="1" pause
exit /b 1

:resolve_install_path
set "_INPUT=%~1"
if "%_INPUT%"=="" goto :eof

if exist "%_INPUT%\resources\settings.properties" (
    set "INSTALL_PATH=%~f1"
    if "!INSTALL_PATH:~-1!"=="\" set "INSTALL_PATH=!INSTALL_PATH:~0,-1!"
    goto :eof
)

if exist "%_INPUT%" (
    if /I "%~nx1"=="run.bat" (
        set "INSTALL_PATH=%~dp1"
        if "!INSTALL_PATH:~-1!"=="\" set "INSTALL_PATH=!INSTALL_PATH:~0,-1!"
        goto :eof
    )
)
goto :eof

:detect_default_install
for %%I in ("%ProgramFiles%\NetNotifyAgent" "%ProgramFiles(x86)%\NetNotifyAgent") do (
    if not defined INSTALL_PATH (
        if exist "%%~fI\resources\settings.properties" set "INSTALL_PATH=%%~fI"
    )
)
goto :eof

:read_current_java_home
set "CURRENT_JAVA_HOME="
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("!SETTINGS_FILE!") do (
    set "_KEY=%%~A"
    set "_VAL=%%~B"
    if /I "!_KEY!"=="java.home" (
        set "CURRENT_JAVA_HOME=!_VAL!"
        set "CURRENT_JAVA_HOME=!CURRENT_JAVA_HOME:"=!"
    )
)

if defined CURRENT_JAVA_HOME (
    set "CURRENT_JAVA_HOME=!CURRENT_JAVA_HOME:/=\!"
    if "!CURRENT_JAVA_HOME:~-1!"=="\" set "CURRENT_JAVA_HOME=!CURRENT_JAVA_HOME:~0,-1!"
    echo java.home atual: !CURRENT_JAVA_HOME!
)
echo.
goto :eof

:resolve_java_home
set "FOUND_JAVA_HOME="

if defined CURRENT_JAVA_HOME (
    if exist "!CURRENT_JAVA_HOME!\bin\java.exe" (
        call :check_version "!CURRENT_JAVA_HOME!\bin\java.exe" CURRENT_MAJOR
        if !CURRENT_MAJOR! GEQ 21 (
            set "FOUND_JAVA_HOME=!CURRENT_JAVA_HOME!"
            echo [OK] java.home atual ja aponta para Java !CURRENT_MAJOR!.
            goto :resolved_java
        ) else (
            echo [AVISO] java.home atual aponta para Java !CURRENT_MAJOR!.
        )
    ) else (
        echo [AVISO] java.home atual nao possui bin\java.exe valido.
    )
    echo.
)

echo Procurando Java 21+ no sistema...

call :check_registry "SOFTWARE\JavaSoft\JDK" FOUND_JAVA_HOME
if defined FOUND_JAVA_HOME goto :resolved_java

call :check_registry "SOFTWARE\JavaSoft\Java Development Kit" FOUND_JAVA_HOME
if defined FOUND_JAVA_HOME goto :resolved_java

call :check_registry_wow64 FOUND_JAVA_HOME
if defined FOUND_JAVA_HOME goto :resolved_java

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        call :check_version "%JAVA_HOME%\bin\java.exe" ENV_MAJOR
        if !ENV_MAJOR! GEQ 21 (
            set "FOUND_JAVA_HOME=%JAVA_HOME%"
            echo [OK] JAVA_HOME aponta para Java !ENV_MAJOR!.
            goto :resolved_java
        )
    )
)

for /f "delims=" %%P in ('where java.exe 2^>nul') do (
    if not defined FOUND_JAVA_HOME (
        call :check_version "%%P" PATH_MAJOR
        if !PATH_MAJOR! GEQ 21 (
            for %%D in ("%%~dpP..") do set "FOUND_JAVA_HOME=%%~fD"
            echo [OK] Java !PATH_MAJOR! encontrado no PATH em %%P
        )
    )
)

:resolved_java
if not defined FOUND_JAVA_HOME (
    echo [ERRO] Nenhum Java 21+ encontrado no sistema.
    echo        Instale o Java 21+ e execute este script novamente.
    goto :eof
)

if "!FOUND_JAVA_HOME:~-1!"=="\" set "FOUND_JAVA_HOME=!FOUND_JAVA_HOME:~0,-1!"
call :check_version "!FOUND_JAVA_HOME!\bin\java.exe" FOUND_MAJOR
echo Java !FOUND_MAJOR! selecionado: !FOUND_JAVA_HOME!
echo.
goto :eof

:update_settings
if /I "!CURRENT_JAVA_HOME!"=="!FOUND_JAVA_HOME!" (
    echo settings.properties ja contem o java.home correto.
    exit /b 0
)

echo Atualizando settings.properties...
copy /y "!SETTINGS_FILE!" "!SETTINGS_FILE!.bak" >nul
if errorlevel 1 (
    echo [ERRO] Nao foi possivel criar backup de settings.properties.
    exit /b 1
)

set "PS_SCRIPT=%TEMP%\_fix_java_home_%RANDOM%.ps1"
(
    echo $file = '!SETTINGS_FILE!'
    echo $javaHome = '!FOUND_JAVA_HOME!'
    echo $lines = Get-Content -LiteralPath $file -Encoding UTF8
    echo $found = $false
    echo $result = foreach ^($line in $lines^) {
    echo     $trimmed = $line.Trim^(
    echo     ^)
    echo     if ^($trimmed -match '^#?\s*java\.home\s*='^) {
    echo         "java.home=$javaHome"
    echo         $found = $true
    echo     ^} else {
    echo         $line
    echo     ^}
    echo ^}
    echo if ^(-not $found^) { $result += "java.home=$javaHome" }
    echo $result ^| Set-Content -LiteralPath $file -Encoding UTF8
) > "!PS_SCRIPT!"

powershell -ExecutionPolicy Bypass -File "!PS_SCRIPT!" >nul 2>&1
set "PS_EXIT=!errorlevel!"
del "!PS_SCRIPT!" 2>nul

if !PS_EXIT! NEQ 0 (
    echo [ERRO] Falha ao atualizar settings.properties.
    copy /y "!SETTINGS_FILE!.bak" "!SETTINGS_FILE!" >nul
    exit /b 1
)

set "SETTINGS_CHANGED=1"
echo [OK] settings.properties atualizado.
echo.
exit /b 0

:repair_legacy_launcher
set "RUN_TEMPLATE=!SCRIPT_DIR!\run.bat.template"
if not exist "!RUN_TEMPLATE!" set "RUN_TEMPLATE=!SCRIPT_DIR!\..\src\main\scripts\run.bat"

set "NEEDS_LAUNCHER_REPAIR=0"
if not exist "!RUN_BAT!" (
    echo [AVISO] run.bat nao encontrado. Um novo launcher sera criado.
    set "NEEDS_LAUNCHER_REPAIR=1"
)

if exist "!RUN_BAT!" (
    findstr /i /c:"JAVA_TMPFILE=" /c:"findstr /i \"version\"" "!RUN_BAT!" >nul 2>&1
    if not errorlevel 1 (
        echo [AVISO] Launcher legado detectado em run.bat.
        set "NEEDS_LAUNCHER_REPAIR=1"
    ) else (
        echo run.bat ja esta em versao atual.
    )
)

if "!NEEDS_LAUNCHER_REPAIR!"=="0" exit /b 0

if not exist "!RUN_TEMPLATE!" (
    echo [ERRO] Template do launcher nao encontrado:
    echo        !RUN_TEMPLATE!
    exit /b 1
)

if exist "!RUN_BAT!" (
    copy /y "!RUN_BAT!" "!RUN_BAT!.legacy.bak" >nul
    if errorlevel 1 (
        echo [ERRO] Nao foi possivel criar backup do run.bat antigo.
        exit /b 1
    )
)

copy /y "!RUN_TEMPLATE!" "!RUN_BAT!" >nul
if errorlevel 1 (
    echo [ERRO] Nao foi possivel gravar o novo run.bat.
    exit /b 1
)

set "RUN_REPAIRED=1"
echo [OK] run.bat atualizado com o launcher novo.
echo.
exit /b 0

:repair_hidden_launcher
set "RUN_HIDDEN_TEMPLATE=!SCRIPT_DIR!\run-hidden.vbs.template"
if not exist "!RUN_HIDDEN_TEMPLATE!" set "RUN_HIDDEN_TEMPLATE=!SCRIPT_DIR!\..\src\main\scripts\run-hidden.vbs"

if not exist "!RUN_HIDDEN_TEMPLATE!" (
    echo [ERRO] Template do launcher oculto nao encontrado:
    echo        !RUN_HIDDEN_TEMPLATE!
    exit /b 1
)

copy /y "!RUN_HIDDEN_TEMPLATE!" "!RUN_HIDDEN!" >nul
if errorlevel 1 (
    echo [ERRO] Nao foi possivel gravar o launcher oculto:
    echo        !RUN_HIDDEN!
    exit /b 1
)

echo [OK] launcher oculto instalado em !RUN_HIDDEN!.
echo.
exit /b 0

:refresh_startup_tasks
if not exist "!RUN_HIDDEN!" (
    echo [ERRO] Launcher oculto nao encontrado para atualizar tasks.
    exit /b 1
)

set "TASK_TARGET=wscript.exe //B //nologo \"!RUN_HIDDEN!\""

call :update_task "NetNotifyAgent"
call :update_tasks_with_prefix "NetNotifyAgent-"
exit /b 0

:update_task
set "_TASK_NAME=%~1"
if "%_TASK_NAME%"=="" goto :eof

schtasks /query /tn "%_TASK_NAME%" >nul 2>&1
if errorlevel 1 goto :eof

schtasks /change /tn "%_TASK_NAME%" /tr "!TASK_TARGET!" >nul 2>&1
if errorlevel 1 (
    echo [AVISO] Nao foi possivel atualizar a task %_TASK_NAME%.
    exit /b 1
)

set "TASKS_UPDATED=1"
echo [OK] Task atualizada para modo silencioso: %_TASK_NAME%
exit /b 0

:update_tasks_with_prefix
set "_TASK_PREFIX=%~1"
if "%_TASK_PREFIX%"=="" goto :eof

for /f "usebackq tokens=1 delims=," %%T in (`schtasks /query /fo csv /nh 2^>nul`) do (
    set "_TASK_NAME=%%~T"
    set "_TASK_NAME=!_TASK_NAME:\=!"
    if /I not "!_TASK_NAME!"=="NetNotifyAgent-RefreshUserTasks" (
        if /I "!_TASK_NAME:~0,15!"=="!_TASK_PREFIX!" (
            call :update_task "!_TASK_NAME!"
        )
    )
)
exit /b 0

:check_version
set "%~2=0"
set "_JEXE=%~1"
for /f "usebackq" %%v in (`powershell -NoProfile -Command "try{$i=[System.Diagnostics.ProcessStartInfo]::new('%_JEXE%','-version');$i.RedirectStandardError=$true;$i.UseShellExecute=$false;$p=[System.Diagnostics.Process]::Start($i);$o=$p.StandardError.ReadToEnd();$p.WaitForExit();if($o-match('version\s+'+[char]34+'(\d+)')){$Matches[1]}else{0}}catch{0}"`) do set "%~2=%%v"
goto :eof

:check_registry
set "%~2="
set "_REGKEY=%~1"
set "_REGVER="
for /f "tokens=3*" %%A in ('reg query "HKLM\!_REGKEY!" /v CurrentVersion 2^>nul') do set "_REGVER=%%A"
if not defined _REGVER goto :eof

set "_REGHOME="
for /f "tokens=3*" %%A in ('reg query "HKLM\!_REGKEY!\!_REGVER!" /v JavaHome 2^>nul') do set "_REGHOME=%%A %%B"
if not defined _REGHOME goto :eof
if "!_REGHOME:~-1!"==" " set "_REGHOME=!_REGHOME:~0,-1!"
if not exist "!_REGHOME!\bin\java.exe" goto :eof

call :check_version "!_REGHOME!\bin\java.exe" REG_MAJOR
if !REG_MAJOR! GEQ 21 (
    set "%~2=!_REGHOME!"
    echo [OK] Registro aponta para Java !REG_MAJOR! em !_REGHOME!
)
goto :eof

:check_registry_wow64
set "%~1="
set "_WOW_VER="
for /f "tokens=3*" %%A in ('reg query "HKLM\SOFTWARE\WOW6432Node\JavaSoft\JDK" /v CurrentVersion 2^>nul') do set "_WOW_VER=%%A"
if not defined _WOW_VER goto :eof

set "_REGHOME32="
for /f "tokens=3*" %%A in ('reg query "HKLM\SOFTWARE\WOW6432Node\JavaSoft\JDK\!_WOW_VER!" /v JavaHome 2^>nul') do set "_REGHOME32=%%A %%B"
if not defined _REGHOME32 goto :eof
if "!_REGHOME32:~-1!"==" " set "_REGHOME32=!_REGHOME32:~0,-1!"
if not exist "!_REGHOME32!\bin\java.exe" goto :eof

call :check_version "!_REGHOME32!\bin\java.exe" WOW_MAJOR
if !WOW_MAJOR! GEQ 21 (
    set "%~1=!_REGHOME32!"
    echo [OK] Registro WOW64 aponta para Java !WOW_MAJOR! em !_REGHOME32!
)
goto :eof
