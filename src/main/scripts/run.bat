@echo off
setlocal EnableDelayedExpansion

rem entrar no diretório do script
cd /d "%~dp0"
set "BASE_DIR=%CD%"
set "JAR_FILE=%BASE_DIR%\${project.artifactId}-${project.version}.jar"
set "LIBS_DIR=%BASE_DIR%\libs"

rem localizar java
set "JAVA_EXE="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
        goto :execute
    )
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        goto :execute
    )
)

where javaw.exe >nul 2>&1
if !errorlevel! equ 0 (
    set "JAVA_EXE=javaw.exe"
    goto :execute
)

where java.exe >nul 2>&1
if !errorlevel! equ 0 (
    set "JAVA_EXE=java.exe"
    goto :execute
)

echo [ERRO] Java nao encontrado. Defina JAVA_HOME ou coloque java no PATH.
pause
exit /b 1

:execute
if not exist "!JAR_FILE!" (
    echo [ERRO] Arquivo principal nao encontrado
    echo Procurando: !JAR_FILE!
    echo.
    echo Certifique-se de que todos os arquivos foram extraidos corretamente.
    pause
    exit /b 1
)

if not exist "!LIBS_DIR!" (
    echo [ERRO] Diretorio libs nao encontrado
    echo Procurando: !LIBS_DIR!
    pause
    exit /b 1
)

rem executar sem usar wildcards na variavel
"!JAVA_EXE!" --module-path "!LIBS_DIR!" --add-modules javafx.controls,javafx.web -cp "!JAR_FILE!;!LIBS_DIR!\*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher

if !errorlevel! neq 0 (
    echo [ERRO] A aplicacao foi encerrada com erro codigo !errorlevel!
    pause
)

exit /b !errorlevel!


