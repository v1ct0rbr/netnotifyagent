@echo off
setlocal EnableExtensions

set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

set "TARGET_INSTALL=F:\Programas"
set "QUIET=0"

:parse_args
if "%~1"=="" goto after_args
if /I "%~1"=="/quiet" (
	set "QUIET=1"
) else (
	set "TARGET_INSTALL=%~1"
)
shift
goto parse_args

:after_args

set "LOG_DIR=%ProgramData%\NetNotifyAgent"
set "LOG_FILE=%LOG_DIR%\fix-java-home.log"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

if "%QUIET%"=="0" (
	echo Iniciando reparo do NetNotify Agent...
	echo Instalacao alvo: "%TARGET_INSTALL%"
	echo Log: "%LOG_FILE%"
	echo.
)

echo [%date% %time%] Iniciando reparo do NetNotify Agent em "%TARGET_INSTALL%" > "%LOG_FILE%"
call "%BASE_DIR%\fix-java-home.bat" "%TARGET_INSTALL%" >> "%LOG_FILE%" 2>&1
set "EXITCODE=%errorlevel%"
echo [%date% %time%] Reparo finalizado com codigo %EXITCODE% >> "%LOG_FILE%"

if "%QUIET%"=="0" (
	echo Reparo finalizado com codigo %EXITCODE%.
	echo Consulte o log em "%LOG_FILE%".
)

exit /b %EXITCODE%
