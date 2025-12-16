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
if %errorlevel% neq 0 (
    echo Aviso: Nao foi possivel parar o servico via net stop
)

REM Aguardar para garantir que o servico parou completamente
timeout /t 3 /nobreak >nul 2>&1

REM Tentar fechar processos relacionados
taskkill /F /IM java.exe >nul 2>&1

REM Aguardar novamente
timeout /t 2 /nobreak >nul 2>&1

REM Remover o servico com retry para erro 1072
echo Removendo o servico "%SERVICE_NAME%"...
set "RETRY=0"
set "MAX_RETRY=3"

:retry_delete
sc.exe delete "%SERVICE_NAME%" >nul 2>&1
set "DELETE_ERROR=!errorlevel!"

if !DELETE_ERROR! equ 0 (
    echo OK: Servico removido com sucesso!
    exit /b 0
) else if !DELETE_ERROR! equ 1072 (
    echo Aviso: Servico marcado para delecao. Tentando limpar...
    REM Aguardar um pouco mais e tentar novamente
    set /a RETRY=RETRY+1
    if !RETRY! leq !MAX_RETRY! (
        timeout /t 2 /nobreak >nul 2>&1
        goto retry_delete
    )
    echo Aviso: Servico sera removido apos reinicializacao do sistema.
    exit /b 0
) else (
    echo Erro ao remover o servico (codigo !DELETE_ERROR!)
    REM Tentar cleanup de registro como ultimo recurso
    echo Tentando limpar registro...
    reg.exe delete "HKLM\SYSTEM\CurrentControlSet\Services\%SERVICE_NAME%" /f >nul 2>&1
    if !errorlevel! equ 0 (
        echo OK: Entrada de registro removida.
        exit /b 0
    ) else (
        exit /b 1
    )
)
