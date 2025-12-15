@echo off
REM Script de desinstalacao do servico NetNotify Agent
REM Parametros:
REM   %1 = Nome do servico (opcional - padrao: NetNotifyAgent)

setlocal EnableDelayedExpansion

REM Verifica se esta executando como administrador
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Erro: Este script requer privilegios de administrador.
    exit /b 1
)

set "SERVICE_NAME=%1"
if "!SERVICE_NAME!"=="" set "SERVICE_NAME=NetNotifyAgent"

echo.
echo ======================================
echo Desinstalando %SERVICE_NAME%
echo ======================================
echo.

REM Verificar se o servico existe
sc.exe query "%SERVICE_NAME%" >nul 2>&1
if %errorlevel% neq 0 (
    echo Aviso: O servico "%SERVICE_NAME%" nao foi encontrado
    exit /b 0
)

REM Parar o servico
echo Parando o servico "%SERVICE_NAME%"...
net stop "%SERVICE_NAME%" /y >nul 2>&1
timeout /t 2 /nobreak >nul 2>&1

REM Remover o servico
echo Removendo o servico "%SERVICE_NAME%"...
sc.exe delete "%SERVICE_NAME%"

if %errorlevel% equ 0 (
    echo OK: Servico removido com sucesso!
    exit /b 0
) else (
    echo Erro ao remover o servico (codigo %errorlevel%)
    exit /b 1
)
