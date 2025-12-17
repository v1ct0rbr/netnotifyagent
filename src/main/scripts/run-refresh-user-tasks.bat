@echo off
setlocal

rem Entrar no diretório de instalação (onde está este script)
cd /d "%~dp0"

rem Executar o script PowerShell de atualização das tasks de usuário
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0refresh-user-tasks.ps1"

exit /b %errorlevel%
