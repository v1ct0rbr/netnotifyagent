@echo off
echo === Build NetNotify Agent ===
echo.

echo 1. Limpando build anterior...
call mvn clean

echo.
echo 2. Compilando e criando executavel...
call mvn package -DskipTests

echo.
echo 3. Verificando arquivos gerados...
if exist "target\netnotifyagent-1.0-SNAPSHOT.jar" (
    echo ✓ Executavel criado: target\netnotifyagent-1.0-SNAPSHOT.jar
) else (
    echo ✗ Falha ao criar executavel
)

if exist "target\netnotifyagent-1.0-SNAPSHOT-dist.zip" (
    echo ✓ Distribuicao criada: target\netnotifyagent-1.0-SNAPSHOT-dist.zip
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
echo   target\netnotifyagent-1.0-SNAPSHOT-dist.zip
echo.
pause