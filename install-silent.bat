@echo off
echo === Instalacao Silenciosa do NetNotify Agent ===
echo.

rem Verificar se o executavel existe
if not exist "NetNotifyAgent-Setup-1.0.0.exe" (
    echo ERRO: NetNotifyAgent-Setup-1.0.0.exe nao encontrado!
    echo Execute o build primeiro.
    pause
    exit /b 1
)

echo Instalando NetNotify Agent silenciosamente...
echo - Instalara em: C:\Program Files\NetNotifyAgent
echo - Criara servico automaticamente
echo - Iniciara com o Windows
echo.

rem Executar instalacao silenciosa
"NetNotifyAgent-Setup-1.0.0.exe" /VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS="installservice"

if %errorlevel% equ 0 (
    echo.
    echo ✓ Instalacao concluida com sucesso!
    echo.
    echo O NetNotify Agent foi instalado como servico e iniciara automaticamente com o Windows.
    echo.
    echo Para verificar o servico:
    echo   Get-Service -Name NetNotifyAgent
    echo.
    echo Para remover:
    echo   "C:\Program Files\NetNotifyAgent\unins000.exe" /VERYSILENT
) else (
    echo.
    echo ✗ Falha na instalacao (codigo %errorlevel%)
    echo Certifique-se de executar como Administrador.
)

echo.
pause