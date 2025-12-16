# Script de instalação de serviço NetNotify Agent
# Execução: PowerShell -ExecutionPolicy Bypass -File install-service.ps1 "C:\caminho\instalacao"

param(
    [string]$InstallPath = (Get-Location).Path,
    [string]$JavaPath = ""
)

# Verificar se está rodando como administrador
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Este script requer privilégios de administrador."
    Write-Host "Reabrindo com privilégios elevados..."
    Start-Process PowerShell -ArgumentList "-ExecutionPolicy Bypass -File `"$PSCommandPath`" `"$InstallPath`" `"$JavaPath`"" -Verb RunAs
    exit 0
}

$ServiceName = "NetNotifyAgent"
$DisplayName = "NetNotify Agent"
$JarFile = "$InstallPath\netnotifyagent-1.0-SNAPSHOT.jar"
$LibsDir = "$InstallPath\libs"
$ServiceBat = "$InstallPath\run-service.bat"

Write-Host ""
Write-Host "======================================"
Write-Host "Instalando $DisplayName"
Write-Host "======================================"
Write-Host ""
Write-Host "Caminho de instalação: $InstallPath"

# Verificar arquivos necessários
if (-not (Test-Path $JarFile)) {
    Write-Host "Erro: JAR não encontrado em $InstallPath"
    exit 1
}

if (-not (Test-Path $LibsDir)) {
    Write-Host "Erro: Pasta libs não encontrada em $InstallPath"
    exit 1
}

# Configurar permissões na pasta resources
$ResourcesPath = "$InstallPath\resources"
if (Test-Path $ResourcesPath) {
    Write-Host "Configurando permissões da pasta resources..."
    icacls "$ResourcesPath" /grant "Users:(OI)(CI)M" /T /C | Out-Null
}

# Remover serviço existente se houver
Write-Host ""
Write-Host "Verificando serviços existentes..."
$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($service) {
    Write-Host "Parando serviço existente..."
    Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    
    Write-Host "Removendo serviço existente..."
    sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 2
}

# Localizar Java
Write-Host ""
Write-Host "Localizando Java..."
$JavaExe = ""

# 1. Verificar se foi fornecido
if ($JavaPath) {
    if (Test-Path "$JavaPath\bin\java.exe") {
        $JavaExe = "$JavaPath\bin\java.exe"
    } elseif (Test-Path $JavaPath) {
        $JavaExe = $JavaPath
    }
}

# 2. Verificar JAVA_HOME
if (-not $JavaExe -and $env:JAVA_HOME) {
    if (Test-Path "$env:JAVA_HOME\bin\java.exe") {
        $JavaExe = "$env:JAVA_HOME\bin\java.exe"
    }
}

# 3. Procurar no PATH
if (-not $JavaExe) {
    $JavaExe = (Get-Command java.exe -ErrorAction SilentlyContinue).Path
}

if (-not $JavaExe) {
    Write-Host "Erro: Java não encontrado"
    Write-Host "Por favor, defina JAVA_HOME ou forneça o caminho do Java"
    exit 1
}

Write-Host "Java encontrado: $JavaExe"

# Criar script de execução do serviço
Write-Host ""
Write-Host "Criando script de execução..."
$ServiceBatContent = @"
@echo off
setlocal EnableDelayedExpansion
set "JAVA_EXE=$JavaExe"
set "INSTALL_PATH=$InstallPath"
set "JAR_FILE=$JarFile"
set "LIBS_DIR=$LibsDir"

if not exist "!JAR_FILE!" exit /b 1
if not exist "!LIBS_DIR!" exit /b 1

"!JAVA_EXE!" --module-path "!LIBS_DIR!" --add-modules javafx.controls,javafx.web -cp "!JAR_FILE!;!LIBS_DIR!\*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher
"@

$ServiceBatContent | Out-File -FilePath $ServiceBat -Encoding ASCII -Force

# Criar serviço usando sc.exe
Write-Host "Criando serviço Windows..."

# Remover se já existe
$existingService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existingService) {
    sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 1
}

# Criar novo serviço
New-Service -Name $ServiceName `
    -DisplayName $DisplayName `
    -BinaryPathName "cmd.exe /c `"$ServiceBat`"" `
    -StartupType Automatic `
    -ErrorAction Stop | Out-Null

Write-Host "OK: Serviço criado com sucesso"

# Configurar timeout
Write-Host "Configurando timeout de inicialização..."
reg.exe add "HKLM\SYSTEM\CurrentControlSet\Services\$ServiceName" /v ServicesPipeTimeout /t REG_DWORD /d 300000 /f | Out-Null

# Adicionar descrição
Write-Host "Adicionando descrição do serviço..."
sc.exe description $ServiceName "Agent para receber notificações via RabbitMQ com interface JavaFX" | Out-Null

# Configurar recuperação
Write-Host "Configurando política de recuperação..."
sc.exe failure $ServiceName reset= 86400 actions= restart/60000 | Out-Null

Write-Host ""
Write-Host "======================================"
Write-Host "Instalação concluída!"
Write-Host "======================================"
Write-Host ""
Write-Host "Para iniciar o serviço agora, execute:"
Write-Host "  Start-Service -Name $ServiceName"
Write-Host ""
Write-Host "Ou via cmd.exe:"
Write-Host "  net start $ServiceName"
Write-Host ""
Write-Host "O serviço iniciará automaticamente na próxima reinicialização."
Write-Host ""

try {
    Write-Host "Iniciando o serviço imediatamente..."
    Start-Service -Name $ServiceName -ErrorAction Stop | Out-Null
    Write-Host "Serviço iniciado com sucesso."
} catch {
    Write-Host "Falha ao iniciar o serviço automaticamente: $($_.Exception.Message)"
    Write-Host "Consulte o Event Viewer em Windows Logs > System para mais detalhes."
}

exit 0
