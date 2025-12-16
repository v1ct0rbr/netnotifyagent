@echo off
REM Script de desinstalacao do servico NetNotify Agent
REM Parametros:
REM   %1 = Nome do servico (opcional - padrao: NetNotifyAgent)

setlocal EnableDelayedExpansion

REM Verifica se esta executando como administrador
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo Erro: Este script requer privilegios de administrador.
    echo Reabrindo com privilégios elevados...
    echo.
    
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%0\" %*' -Verb RunAs" >nul 2>&1
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
    exit /b 0
)

REM Parar o servico
echo Parando o servico "!SERVICE_NAME!"...
net stop "!SERVICE_NAME!" /y >nul 2>&1

REM Aguardar curto e forcadamente encerrar processos
timeout /t 1 /nobreak >nul 2>&1
echo Encerrando processos...
taskkill /F /IM java.exe /T >nul 2>&1
taskkill /F /IM javaw.exe /T >nul 2>&1
timeout /t 1 /nobreak >nul 2>&1

REM Remover o servico
echo.
echo Removendo o servico "!SERVICE_NAME!"...
sc.exe delete "!SERVICE_NAME!" >nul 2>&1
set "DELETE_ERROR=!errorlevel!"

if !DELETE_ERROR! equ 0 (
    echo OK: Servico removido com sucesso!
    goto cleanup_success
) else if !DELETE_ERROR! equ 1072 (
    REM Servico marcado para delecao - tentar via Registro
    echo Tentando limpeza via Registro...
    goto cleanup_registry
) else if !DELETE_ERROR! equ 1060 (
    REM Servico nao existe
    echo OK: Servico nao encontrado (ja foi removido).
    goto cleanup_success
) else (
    echo Erro !DELETE_ERROR!. Tentando limpeza via Registro...
    goto cleanup_registry
)

:cleanup_registry
reg.exe delete "HKLM\SYSTEM\CurrentControlSet\Services\!SERVICE_NAME!" /f >nul 2>&1
if !errorlevel! equ 0 (
    echo OK: Entrada de registro removida.
) else (
    echo Aviso: Nao foi possivel remover do registro.
)

:cleanup_success
echo.
echo ======================================
echo Desinstalacao concluida!
echo ======================================
exit /b 0
