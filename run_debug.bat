@echo off
REM Script para executar a aplicação com saída de DEBUG

cd /d "%~dp0"

REM Verificar se Java está disponível
java -version >nul 2>&1
if errorlevel 1 (
    echo Erro: Java não encontrado no PATH
    pause
    exit /b 1
)

echo.
echo Iniciando NetNotify Agent com DEBUG logs...
echo.

REM Verificar versão do Java (necessário Java 21+)
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%g
)
set JAVA_VER=%JAVA_VER:"=%
for /f "delims=. tokens=1" %%v in ("%JAVA_VER%") do set JAVA_MAJOR=%%v
if %JAVA_MAJOR% LSS 21 (
    echo Erro: Java 21+ e necessario. Versao atual: %JAVA_VER%
    pause
    exit /b 1
)
REM Executar com --module-path para garantir que JavaFX 22 seja carregado (não o JavaFX 17 do JDK)
java --module-path "target\libs" --add-modules javafx.controls,javafx.web,javafx.swing,javafx.fxml,javafx.media -cp "target\netnotifyagent-1.0-SNAPSHOT.jar;target\libs\*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher

pause
