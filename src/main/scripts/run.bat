@echo off
setlocal EnableDelayedExpansion

rem entrar no diretório do script
cd /d "%~dp0"
set "BASE_DIR=%CD%"
set "JAR_FILE=%BASE_DIR%\${project.artifactId}-${project.version}.jar"
set "LIBS_DIR=%BASE_DIR%\libs"
set "SETTINGS_FILE=%BASE_DIR%\resources\settings.properties"

rem tentar localizar java via settings.properties (java.executable/java.home)
set "JAVA_EXE="
set "CFG_JAVA_EXE="
set "CFG_JAVA_HOME="
if exist "%SETTINGS_FILE%" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%SETTINGS_FILE%") do (
        set "KEY=%%~A"
        set "VAL=%%~B"
        if /I "!KEY!"=="java.executable" set "CFG_JAVA_EXE=!VAL!"
        if /I "!KEY!"=="java.home" set "CFG_JAVA_HOME=!VAL!"
    )
    rem remover aspas caso existam
    if defined CFG_JAVA_EXE set "CFG_JAVA_EXE=!CFG_JAVA_EXE:\"=!"
    if defined CFG_JAVA_HOME set "CFG_JAVA_HOME=!CFG_JAVA_HOME:\"=!"
    rem normalizar separadores invertidos
    if defined CFG_JAVA_HOME (
        set "CFG_JAVA_HOME=!CFG_JAVA_HOME:/=\!"
    )
    if defined CFG_JAVA_EXE (
        if exist "!CFG_JAVA_EXE!" (
            set "JAVA_EXE=!CFG_JAVA_EXE!"
            goto :execute
        )
    )
    if defined CFG_JAVA_HOME (
        if exist "!CFG_JAVA_HOME!\bin\javaw.exe" (
            set "JAVA_EXE=!CFG_JAVA_HOME!\bin\javaw.exe"
            goto :execute
        )
        if exist "!CFG_JAVA_HOME!\bin\java.exe" (
            set "JAVA_EXE=!CFG_JAVA_HOME!\bin\java.exe"
            goto :execute
        )
    )
)

rem localizar java via ambiente (JAVA_HOME/PATH)
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
start "" "!JAVA_EXE!" --module-path "!LIBS_DIR!" --add-modules javafx.controls,javafx.web -cp "!JAR_FILE!;!LIBS_DIR!\*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher

exit /b 0


