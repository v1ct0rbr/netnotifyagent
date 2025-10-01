@echo off
setlocal EnableDelayedExpansion
title NetNotify Agent

:: Configurar diretório base
set "BASE_DIR=%~dp0"
set "JAR_FILE=%BASE_DIR%${project.artifactId}-${project.version}.jar"
set "LIBS_DIR=%BASE_DIR%libs"

echo ===============================================
echo         NetNotify Agent v${project.version}
echo ===============================================
echo.

:: Verificar se JAR existe
if not exist "%JAR_FILE%" (
    echo [ERRO] Arquivo principal não encontrado!
    echo Procurando: %JAR_FILE%
    echo.
    echo Certifique-se de que todos os arquivos foram extraídos corretamente.
    echo.
    pause
    exit /b 1
)

:: Verificar se diretório libs existe
if not exist "%LIBS_DIR%" (
    echo [ERRO] Diretório de dependências não encontrado!
    echo Procurando: %LIBS_DIR%
    echo.
    echo Certifique-se de que a pasta 'libs' está presente.
    echo.
    pause
    exit /b 1
)

:: Verificar Java
java -version >nul 2>&1
if !errorlevel! neq 0 (
    echo [ERRO] Java não encontrado!
    echo.
    echo Este aplicativo requer Java 11 ou superior.
    echo Baixe a versão mais recente em: https://adoptium.net/temurin/releases
    echo.
    pause
    exit /b 1
)

:: Obter versão do Java para validação
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
set JAVA_VERSION=!JAVA_VERSION:"=!

echo [INFO] Java detectado: !JAVA_VERSION!
echo [INFO] JAR: %JAR_FILE%
echo [INFO] Libs: %LIBS_DIR%
echo.
echo Iniciando NetNotify Agent...
echo.

:: Executar aplicação com configuração completa
java --module-path "%LIBS_DIR%" --add-modules javafx.controls,javafx.web -cp "%JAR_FILE%;%LIBS_DIR%\*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher

:: Verificar resultado da execução
if !errorlevel! equ 0 (
    echo.
    echo [INFO] NetNotify Agent encerrado normalmente.
) else (
    echo.
    echo [ERRO] A aplicação foi encerrada com código de erro: !errorlevel!
    echo.
    echo Possíveis causas:
    echo - Erro de configuração do RabbitMQ
    echo - JavaFX não foi inicializado corretamente
    echo - Problema de permissões
    echo.
    pause
)


