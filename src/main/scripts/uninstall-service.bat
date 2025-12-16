@echo off
REM Script de desinstalacao do servico NetNotify Agent
REM Parametros:
REM   %1 = Nome do servico (opcional - padrao: NetNotifyAgent)

setlocal EnableDelayedExpansion

REM Se chamado com /hide, mostrar a janela
if "%1"=="/hide" (
    shift
    start cmd /k "%0" %*
    exit /b 0
)

REM Verifica se esta executando como administrador
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo Erro: Este script requer privilegios de administrador.
    echo Reabrindo com privilégios elevados...
    echo.
    
    REM Tentar elevar privilégios usando PowerShell
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%0\"' -Verb RunAs"
    exit /b 0
)

set "SERVICE_NAME=%1"
if "!SERVICE_NAME!"=="" set "SERVICE_NAME=NetNotifyAgent"

echo.
echo ======================================
echo Desinstalando !SERVICE_NAME!
echo ======================================
echo.

REM Verificar se o servico existe
sc.exe query "!SERVICE_NAME!" >nul 2>&1
if !errorlevel! neq 0 (
    echo Aviso: O servico "!SERVICE_NAME!" nao foi encontrado
    pause
    exit /b 0
)

REM Parar o servico
echo Parando o servico "!SERVICE_NAME!"...
net stop "!SERVICE_NAME!" /y 2>&1

REM Verificar status do servico e aguardar sua parada
echo Aguardando parada do servico...
set "STATUS_CHECK=0"
:wait_service_stop
sc.exe query "!SERVICE_NAME!" 2>nul | find "STOPPED" >nul
if errorlevel 1 (
    set /a STATUS_CHECK=STATUS_CHECK+1
    if !STATUS_CHECK! leq 15 (
        timeout /t 1 /nobreak >nul 2>&1
        goto wait_service_stop
    )
)

echo OK: Servico parado.
timeout /t 2 /nobreak >nul 2>&1

REM Tentar fechar processos relacionados de forma mais agressiva
echo Encerrando processos Java...
taskkill /F /IM java.exe /T 2>&1 | find /v "nao encontrado" 2>nul
taskkill /F /IM javaw.exe /T 2>&1 | find /v "nao encontrado" 2>nul

REM Aguardar para liberar locks
echo Aguardando liberacao de arquivo...
timeout /t 3 /nobreak >nul 2>&1

REM Remover o servico com retry para erro 1072
echo.
echo Removendo o servico "!SERVICE_NAME!"...
set "RETRY=0"
set "MAX_RETRY=15"

:retry_delete
sc.exe delete "!SERVICE_NAME!" 2>&1
set "DELETE_ERROR=!errorlevel!"

if !DELETE_ERROR! equ 0 (
    echo.
    echo ======================================
    echo OK: Servico removido com sucesso!
    echo ======================================
    timeout /t 2 /nobreak >nul 2>&1
    pause
    exit /b 0
) else if !DELETE_ERROR! equ 1072 (
    REM Servico marcado para delecao ou ainda nao parou completamente
    set /a RETRY=RETRY+1
    if !RETRY! leq !MAX_RETRY! (
        echo Tentativa !RETRY! de !MAX_RETRY! - Aguardando liberacao do servico...
        timeout /t 2 /nobreak >nul 2>&1
        goto retry_delete
    ) else (
        echo Limite de tentativas atingido. Tentando limpar pelo Registro...
    )
)

REM Tentar limpeza pelo Registro como opcao final
echo.
echo Limpando entrada do Registro...
reg.exe delete "HKLM\SYSTEM\CurrentControlSet\Services\!SERVICE_NAME!" /f 2>&1
if !errorlevel! equ 0 (
    echo OK: Entrada de registro removida.
    echo Nota: Uma reinicializacao pode ser necessaria para completar a limpeza.
    timeout /t 2 /nobreak >nul 2>&1
    pause
    exit /b 0
) else (
    echo Erro ao remover do registro (codigo !errorlevel!).
    echo Tente reiniciar o computador e executar novamente.
    pause
    exit /b 1
)
