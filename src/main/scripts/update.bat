@echo off
setlocal
call "%~dp0refresh-agent.bat" %*
exit /b %errorlevel%
