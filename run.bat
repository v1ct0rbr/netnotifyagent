
@echo off
REM Run Netnotifyagent on Windows (cmd.exe)
REM Build first: mvn clean package -DskipTests

set SCRIPT_DIR=%~dp0
set JAVA=java
set DEPS_DIR=%SCRIPT_DIR%target\dependency
set CLASSES_DIR=%SCRIPT_DIR%target\classes

REM If dependencies are missing, try to copy via Maven
if not exist "%DEPS_DIR%\*" (
	echo Dependencies not found in %DEPS_DIR%. Running mvn dependency:copy-dependencies package -DskipTests...
	pushd %SCRIPT_DIR%
	mvn dependency:copy-dependencies package -DskipTests || (
		echo Failed to copy dependencies. Please run: mvn dependency:copy-dependencies package -DskipTests
		popd
		exit /b 1
	)
	popd
)

set MODULE_PATH=%DEPS_DIR%
set CP=%CLASSES_DIR%;%DEPS_DIR%\*

%JAVA% --module-path "%MODULE_PATH%" --add-modules javafx.controls,javafx.web -cp "%CP%" br.gov.pb.der.netnotifyagent.Netnotifyagent


