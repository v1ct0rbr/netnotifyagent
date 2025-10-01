# PowerShell: create a Windows service that runs javaw (no console).
param(
    [string]$ServiceName = "NetNotifyAgent",
    [string]$DisplayName = "NetNotify Agent",
    [string]$BaseDir = $PSScriptRoot
)

Write-Host "=== NetNotify Agent Service Installer ==="
Write-Host "BaseDir: $BaseDir"
Write-Host "ServiceName: $ServiceName"

$jar = Join-Path $BaseDir "netnotifyagent-1.0-SNAPSHOT.jar"
$libs = Join-Path $BaseDir "libs"

# choose javaw (compatível com PowerShell 5.1)
$javaw = $null
if ($env:JAVA_HOME) {
    $javaw = Join-Path $env:JAVA_HOME "bin\javaw.exe"
    if (-not (Test-Path $javaw)) {
        $javaw = $null
    }
}

if (-not $javaw) {
    try {
        $javawCmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
        if ($javawCmd) {
            $javaw = $javawCmd.Source
        }
    } catch {
        # Ignore
    }
}

if (-not $javaw -or -not (Test-Path $javaw)) {
    Write-Error "javaw not found. Set JAVA_HOME or install javaw on PATH."
    exit 1
}

if (-not (Test-Path $jar)) { 
    Write-Error "JAR not found: $jar"
    exit 1 
}

if (-not (Test-Path $libs)) { 
    Write-Error "libs not found: $libs"
    exit 1 
}

Write-Host "Using Java: $javaw"
Write-Host "JAR: $jar"
Write-Host "Libs: $libs"

# Parar e remover serviço existente se houver
try {
    $existingService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($existingService) {
        Write-Host "Parando servico existente..."
        Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
        Write-Host "Removendo servico existente..."
        sc.exe delete $ServiceName | Out-Null
        Start-Sleep -Seconds 2
    }
} catch {
    Write-Host "Nenhum servico existente encontrado."
}

# Criar comando do serviço
$binPath = "`"$javaw`" --module-path `"$libs`" --add-modules javafx.controls,javafx.web -cp `"$jar`;$libs\*`" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher"

Write-Host "Criando servico: $ServiceName"
Write-Host "Comando: $binPath"

# Criar serviço com sc.exe
$result = sc.exe create $ServiceName binPath= $binPath start= auto DisplayName= "$DisplayName" type= own
if ($LASTEXITCODE -eq 0) {
    Write-Host "Servico criado com sucesso!"
    
    # Configurar descrição
    sc.exe description $ServiceName "Agent for receiving notifications via RabbitMQ with JavaFX interface"
    
    # Configurar recuperação em caso de falha
    sc.exe failure $ServiceName reset= 86400 actions= restart/30000/restart/30000/restart/30000
    
    Write-Host "Iniciando servico..."
    $startResult = sc.exe start $ServiceName
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Servico iniciado com sucesso!"
        
        # Verificar status
        Start-Sleep -Seconds 3
        $serviceStatus = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
        if ($serviceStatus -and $serviceStatus.Status -eq 'Running') {
            Write-Host "Servico esta executando corretamente!"
        } else {
            Write-Warning "Servico foi criado mas pode nao estar executando. Verifique os logs."
        }
    } else {
        Write-Warning "Servico foi criado mas falhou ao iniciar (codigo $LASTEXITCODE)."
        Write-Host "Verifique se o Java e as dependencias estao corretos."
    }
} else {
    Write-Error "Falha ao criar servico (codigo $LASTEXITCODE)."
    Write-Host "Certifique-se de executar como Administrador."
    exit 1
}

Write-Host ""
Write-Host "=== Instalacao do Servico Concluida ==="
Write-Host "Para verificar: Get-Service -Name $ServiceName"
Write-Host "Para parar: Stop-Service -Name $ServiceName"
Write-Host "Para remover: sc.exe delete $ServiceName"