@echo off
REM Launcher wrapper para o servico NetNotify Agent
REM Este arquivo eh chamado pelo Windows Service Manager

setlocal EnableDelayedExpansion

REM Obter o diretorio onde este script esta localizado
set "SCRIPT_DIR=%~dp0"
if "!SCRIPT_DIR:~-1!"=="\" set "SCRIPT_DIR=!SCRIPT_DIR:~0,-1!"

set "JAR=!SCRIPT_DIR!\netnotifyagent-1.0-SNAPSHOT.jar"
set "LIBS=!SCRIPT_DIR!\libs"

REM Encontrar javaw.exe
set "JAVAW="

if defined JAVA_HOME (
    if exist "!JAVA_HOME!\bin\javaw.exe" (
        set "JAVAW=!JAVA_HOME!\bin\javaw.exe"
    )
)

if "!JAVAW!"=="" (
    for %%X in (javaw.exe) do (set "JAVAW=%%~$PATH:X")
)

if "!JAVAW!"=="" (
    exit /b 1
)

REM Executar o aplicativo
"!JAVAW!" --module-path "!LIBS!" --add-modules javafx.controls,javafx.web -cp "!JAR!;!LIBS!\*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher

REM Preservar codigo de saida
exit /b %errorlevel%
