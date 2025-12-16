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
    REM Usar diretório atual
    cd /d "%cd%"
    set "INSTALL_PATH=%cd%"
    
    REM Tentar ler do settings.properties no diretório atual
    if exist "%INSTALL_PATH%\resources\settings.properties" (
        for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%INSTALL_PATH%\resources\settings.properties") do (
            set "KEY=%%~A"
            set "VAL=%%~B"
            if /I "!KEY!"=="install.path" (
                if not "!VAL!"=="" set "INSTALL_PATH=!VAL!"
            )
        )
    )
) else (
    set "INSTALL_PATH=%~1"
)

set "PROVIDED_JAVA=%~2"
set "SERVICE_NAME=NetNotifyAgent"
set "DISPLAY_NAME=NetNotify Agent"

REM Garantir que INSTALL_PATH foi definido corretamente
if "!INSTALL_PATH!"=="" (
    echo Erro: Caminho de instalacao nao foi definido corretamente
    pause
    exit /b 1
)

echo Caminho de instalacao: !INSTALL_PATH!
echo.

REM Verifica se o JAR existe
if not exist "!INSTALL_PATH!\netnotifyagent-1.0-SNAPSHOT.jar" (
    echo Erro: JAR nao encontrado em !INSTALL_PATH!
    echo.
    echo Arquivos encontrados em !INSTALL_PATH!:
    dir "!INSTALL_PATH!" /B
    pause
    exit /b 1
)

REM Verifica se a pasta libs existe
if not exist "!INSTALL_PATH!\libs" (
    echo Erro: Pasta libs nao encontrada em !INSTALL_PATH!
    pause
    exit /b 1
)

echo.
echo ======================================
echo Instalando !DISPLAY_NAME!
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

REM ===== CONFIGURAR PARAMETROS DO SERVICO =====
set "JAR_FILE=!INSTALL_PATH!\netnotifyagent-1.0-SNAPSHOT.jar"
set "LIBS_DIR=!INSTALL_PATH!\libs"

REM Verificar arquivos necessarios
if not exist "!JAR_FILE!" (
    echo Erro: JAR nao encontrado em !INSTALL_PATH!
    pause
    exit /b 1
)

if not exist "!LIBS_DIR!" (
    echo Erro: Libs nao encontrado em !INSTALL_PATH!
    pause
    exit /b 1
)

echo Criando servico: !SERVICE_NAME!
echo Diretorio: !INSTALL_PATH!
echo Java: !JAVA_EXE!
echo.

REM ===== CRIAR O SERVICO =====
REM Executar Java diretamente para evitar erro 1053
REM Usar javaw.exe para modo console-less (melhor para servicos)
set "JAVA_CMD=!JAVA_EXE!"
REM Se encontramos java.exe, tentar usar javaw.exe em preferencia
if "!JAVA_CMD:java.exe=!"  neq "!JAVA_CMD!" (
    set "JAVA_PARENT_DIR=!JAVA_EXE:\java.exe=!"
    if exist "!JAVA_PARENT_DIR!\javaw.exe" (
        set "JAVA_CMD=!JAVA_PARENT_DIR!\javaw.exe"
    )
)

REM Construir o comando completo para o serviço
set "JAVA_ARGS=--module-path \"!LIBS_DIR!\" --add-modules javafx.controls,javafx.web -cp \"!JAR_FILE!;!LIBS_DIR!\*\" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher"
set "SERVICE_BINPATH=!JAVA_CMD! !JAVA_ARGS!"

REM Criar o serviço com o comando Java direto
sc.exe create !SERVICE_NAME! binPath= "!SERVICE_BINPATH!" displayName= "!DISPLAY_NAME!" start= auto type= own error= critical

if !errorlevel! neq 0 (
    echo Erro ao criar o servico (codigo !errorlevel!)
    pause
    exit /b 1
)

echo OK: Servico criado com sucesso!

REM Configurar descricao
sc.exe description !SERVICE_NAME! "Agent para receber notificacoes via RabbitMQ com interface JavaFX"

REM Configurar timeout de inicializacao (em milisegundos)
REM Windows pode precisar de mais tempo para carregar Java
reg.exe add "HKLM\SYSTEM\CurrentControlSet\Services\!SERVICE_NAME!" /v ServicesPipeTimeout /t REG_DWORD /d 180000 /f >nul 2>&1

REM Configurar recuperacao (reiniciar em caso de falha)
sc.exe failure !SERVICE_NAME! reset= 86400 actions= restart/30000/restart/30000/restart/30000

REM Configurar prioridade
REM wmic Service where name="!SERVICE_NAME!" call setpriority 32 >nul 2>&1

REM ===== INICIAR O SERVICO =====
echo.
echo Inicializando servico (aguarde até 3 minutos)...
timeout /t 2 /nobreak >nul 2>&1

net start !SERVICE_NAME!

if !errorlevel! equ 0 (
    echo.
    echo OK: Servico iniciado com sucesso!
    echo.
    echo ======================================
    echo Instalacao concluida!
    echo ======================================
    pause
    exit /b 0
) else if !errorlevel! equ 1056 (
    echo.
    echo OK: Servico ja estava em execucao
    echo.
    echo ======================================
    echo Instalacao concluida!
    echo ======================================
    pause
    exit /b 0
) else (
    echo.
    echo Aviso: Servico foi criado mas pode estar com problemas ao iniciar
    echo Verifique o Event Viewer para mais detalhes
    echo Codigo de erro: !errorlevel!
    pause
    exit /b 1
)
