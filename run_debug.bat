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

REM Executar a aplicação
java -cp "target\netnotifyagent-1.0-SNAPSHOT.jar;target\libs\*" br.gov.pb.der.netnotifyagent.Netnotifyagent

pause
