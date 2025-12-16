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
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%0\"' -Verb RunAs" >nul 2>&1
    if !errorlevel! equ 0 (
        exit /b 0
    ) else (
        echo.
        echo Falha ao elevar privilégios. Por favor, execute este script como administrador.
        pause
        exit /b 1
    )
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
    pause
    exit /b 0
)

REM Parar o servico
echo Parando o servico "%SERVICE_NAME%"...
net stop "%SERVICE_NAME%" /y
if !errorlevel! neq 0 (
    echo Aviso: O servico pode estar parado ou bloqueado
)

REM Aguardar para garantir que o servico parou completamente
timeout /t 2 /nobreak

REM Tentar fechar processos relacionados de forma mais agressiva
echo Matando processos java.exe...
taskkill /F /IM java.exe
taskkill /F /IM cmd.exe

REM Aguardar mais tempo para liberar locks
timeout /t 3 /nobreak

REM Remover o servico com retry para erro 1072
echo Removendo o servico "%SERVICE_NAME%"...
set "RETRY=0"
set "MAX_RETRY=5"

:retry_delete
sc.exe delete "%SERVICE_NAME%"
set "DELETE_ERROR=!errorlevel!"

if !DELETE_ERROR! equ 0 (
    echo OK: Servico removido com sucesso!
    timeout /t 1 /nobreak
    pause
    exit /b 0
) else if !DELETE_ERROR! equ 1072 (
    REM Servico marcado para delecao, tentar novamente com delay maior
    set /a RETRY=RETRY+1
    echo Tentativa !RETRY! de !MAX_RETRY! (Servico marcado para delecao)...
    timeout /t 3 /nobreak
    
    if !RETRY! leq !MAX_RETRY! (
        goto retry_delete
    )
    
    REM Se ainda nao conseguiu, remover do registro
    echo Removendo do registro do Windows...
    reg.exe delete "HKLM\SYSTEM\CurrentControlSet\Services\%SERVICE_NAME%" /f
    echo OK: Entrada de registro removida. Reinicializacao pode ser necessaria.
    pause
    exit /b 0
) else (
    echo Erro ao remover o servico (codigo !DELETE_ERROR!)
    REM Tentar cleanup de registro como ultimo recurso
    echo Tentando limpar registro...
    reg.exe delete "HKLM\SYSTEM\CurrentControlSet\Services\%SERVICE_NAME%" /f
    if !errorlevel! equ 0 (
        echo OK: Entrada de registro removida.
        pause
        exit /b 0
    ) else (
        echo Falha ao remover registro
        pause
        exit /b 1
    )
)

echo.
echo ======================================
echo Desinstalacao finalizada
echo ======================================
echo.
pause
