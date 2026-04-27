@echo off
echo === Build NetNotify Agent ===
echo.

set "JAVA_HOME=D:\Programas\java\openjdk-21"
set "MAVEN_HOME=D:\Programas\apache-maven-3.9.11"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

echo Usando Java: %JAVA_HOME%
java -version 2>&1 | findstr /i "version"
echo.

echo 1. Limpando build anterior...
call mvn clean

echo.
echo 2. Compilando e criando executavel...
call mvn package -DskipTests

echo.
echo 3. Verificando arquivos gerados...
if exist "target\netnotifyagent-1.5-SNAPSHOT.jar" (
    echo ✓ Executavel criado: target\netnotifyagent-1.0-SNAPSHOT.jar
) else (
    echo ✗ Falha ao criar executavel
)

if exist "target\netnotifyagent-1.5-SNAPSHOT-dist.zip" (
    echo ✓ Distribuicao criada: target\netnotifyagent-1.5-SNAPSHOT-dist.zip
) else (
    echo ✗ Falha ao criar distribuicao
)

echo.
echo === Build Concluido ===
echo.
echo Para testar:
echo   target\run.bat ou target\run.sh
echo.
echo Para distribuir:
echo   target\netnotifyagent-1.5-SNAPSHOT-dist.zip
echo.
pause