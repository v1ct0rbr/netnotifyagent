@echo off
setlocal

REM Wrapper simples para executar o refresh-agent.bat sem quebrar no ESET
REM Passa todos os argumentos recebidos (%*) diretamente para o script real

if not exist "C:\netnotify-update\refresh-agent.bat" (
    echo [ERRO] Arquivo refresh-agent.bat nao encontrado em C:\netnotify-update
    exit /b 1
)

call "C:\netnotify-update\refresh-agent.bat" %*

endlocal
exit /b 0
