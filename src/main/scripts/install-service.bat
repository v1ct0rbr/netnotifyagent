@echo off
REM Script de instalacao do servico NetNotify Agent
REM Parametros:
REM   %1 = Caminho de instalacao (opcional - usa diretorio atual se nao fornecido)
REM   %2 = Caminho do Java (opcional se detectar no sistema)

setlocal EnableDelayedExpansion

REM Verifica se esta executando como administrador
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo Erro: Este script requer privilegios de administrador.
    echo Reabrindo com privilégios elevados...
    echo.
    
    REM Tentar elevar privilégios usando PowerShell
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%0\" %*' -Verb RunAs" >nul 2>&1
    if !errorlevel! equ 0 (
        exit /b 0
    ) else (
        echo.
        echo Falha ao elevar privilégios. Por favor, execute este script como administrador.
        pause
        exit /b 1
    )
)

REM Obtem parametros - se nao fornecido, tenta ler do settings.properties
if "%~1"=="" (
    set "SCRIPT_DIR=%~dp0"
    
    REM Se %~dp0 está vazio, usar %cd% (diretório atual)
    if "!SCRIPT_DIR!"=="" (
        set "SCRIPT_DIR=%cd%"
    )
    
    REM Garantir que termina com barra
    if not "!SCRIPT_DIR:~-1!"=="\" set "SCRIPT_DIR=!SCRIPT_DIR!\"
    
    REM Tentar ler do settings.properties
    set "SETTINGS_FILE=!SCRIPT_DIR!resources\settings.properties"
    if exist "!SETTINGS_FILE!" (
        for /f "usebackq eol=# tokens=1,* delims==" %%A in ("!SETTINGS_FILE!") do (
            set "KEY=%%~A"
            set "VAL=%%~B"
            if /I "!KEY!"=="install.path" (
                if not "!VAL!"=="" set "INSTALL_PATH=!VAL!"
            )
        )
    )
    
    REM Se não conseguiu ler do settings, usar o diretório atual
    if "!INSTALL_PATH!"=="" (
        set "INSTALL_PATH=!SCRIPT_DIR!"
        REM Remove barra final se existir
        if "!INSTALL_PATH:~-1!"=="\" set "INSTALL_PATH=!INSTALL_PATH:~0,-1!"
    )
) else (
    set "INSTALL_PATH=%~1"
)

set "PROVIDED_JAVA=%~2"
set "SERVICE_NAME=NetNotifyAgent"
set "DISPLAY_NAME=NetNotify Agent"

echo Caminho de instalacao: !INSTALL_PATH!
echo.

REM Verifica se o JAR existe
if not exist "!INSTALL_PATH!\netnotifyagent-1.0-SNAPSHOT.jar" (
    echo Erro: JAR nao encontrado em !INSTALL_PATH!
    exit /b 1
)

REM Verifica se a pasta libs existe
if not exist "!INSTALL_PATH!\libs" (
    echo Erro: Pasta libs nao encontrada em !INSTALL_PATH!
    exit /b 1
)

echo.
echo ======================================
echo Instalando %DISPLAY_NAME%
echo ======================================
echo.

REM ===== CONFIGURAR PERMISSOES NA PASTA DE RESOURCES =====
set "RESOURCES_PATH=!INSTALL_PATH!\resources"
if exist "!RESOURCES_PATH!" (
    echo Configurando permissoes da pasta resources...
    icacls "!RESOURCES_PATH!" /grant "Users:(OI)(CI)M" /T /C >nul 2>&1
    echo OK: Permissoes configuradas.
)

REM ===== GERENCIAR SERVICO EXISTENTE =====
echo.
echo Verificando servicos existentes...
sc.exe query %SERVICE_NAME% >nul 2>&1
if %errorlevel% equ 0 (
    echo Parando servico existente...
    net stop %SERVICE_NAME% /y >nul 2>&1
    timeout /t 1 /nobreak >nul 2>&1
    
    echo Removendo servico existente...
    sc.exe delete %SERVICE_NAME% >nul 2>&1
    timeout /t 1 /nobreak >nul 2>&1
)

REM ===== LOCALIZAR JAVA =====
set "JAVA_EXE="

if defined PROVIDED_JAVA (
    if exist "%PROVIDED_JAVA%\bin\java.exe" (
        set "JAVA_EXE=%PROVIDED_JAVA%\bin\java.exe"
    ) else if exist "%PROVIDED_JAVA%" (
        set "JAVA_EXE=%PROVIDED_JAVA%"
    )
)

if not defined JAVA_EXE (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" (
            set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        )
    )
)

if not defined JAVA_EXE (
    where java.exe >nul 2>&1
    if !errorlevel! equ 0 (
        for /f "tokens=*" %%i in ('where java.exe') do set "JAVA_EXE=%%i"
    )
)

if not defined JAVA_EXE (
    echo Erro: Java nao encontrado!
    echo Por favor, defina JAVA_HOME ou forneca o caminho do Java
    exit /b 1
)

echo Java encontrado: !JAVA_EXE!
echo.

REM ===== USAR LAUNCHER WRAPPER =====
set "LAUNCHER=!INSTALL_PATH!\run.bat"

REM Verificar se o launcher existe
if not exist "!LAUNCHER!" (
    echo Erro: run.bat nao encontrado em !INSTALL_PATH!
    echo Copie o arquivo run.bat para o diretorio de instalacao
    exit /b 1
)

echo Criando servico: %SERVICE_NAME%
echo Diretorio: !INSTALL_PATH!
echo Java: !JAVA_EXE!
echo.

REM ===== CRIAR O SERVICO =====
REM Usar cmd.exe para executar o script batch, pois servicos Windows nao conseguem rodar .bat diretamente
sc.exe create %SERVICE_NAME% binPath= "cmd /c \"!LAUNCHER!\"" displayName= "%DISPLAY_NAME%" start= auto type= own error= critical

if %errorlevel% neq 0 (
    echo Erro ao criar o servico (codigo %errorlevel%)
    exit /b 1
)

echo OK: Servico criado com sucesso!

REM Configurar descricao
sc.exe description %SERVICE_NAME% "Agent para receber notificacoes via RabbitMQ com interface JavaFX"

REM Configurar recuperacao (reiniciar em caso de falha)
sc.exe failure %SERVICE_NAME% reset= 86400 actions= restart/30000/restart/30000/restart/30000

REM ===== INICIAR O SERVICO =====
echo.
echo Iniciando servico...
net start %SERVICE_NAME%

if %errorlevel% equ 0 (
    echo OK: Servico iniciado com sucesso!
    echo.
    echo ======================================
    echo Instalacao concluida!
    echo ======================================
    exit /b 0
) else (
    echo Aviso: Servico foi criado mas pode estar com problemas ao iniciar
    exit /b 0
)
