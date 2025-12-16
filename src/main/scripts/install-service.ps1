# Script de instalação de serviço NetNotify Agent
# Execução: PowerShell -ExecutionPolicy Bypass -File install-service.ps1 "C:\caminho\instalacao"

param(
    [string]$InstallPath = (Get-Location).Path,
    [string]$JavaPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Throw-Error {
    param(
        [string]$Message,
        [int]$Code = 1
    )
    Write-Host $Message
    exit $Code
}

function Resolve-AbsolutePath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    $trimmed = $Path.Trim('"')
    try {
        return (Resolve-Path -LiteralPath $trimmed -ErrorAction Stop).ProviderPath
    } catch {
        return $null
    }
}

function Get-AgentJar {
    param([string]$BasePath)
    $pattern = Join-Path $BasePath 'netnotifyagent-*.jar'
    $candidate = Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($candidate) {
        return $candidate.FullName
    }
    return $null
}

function Resolve-JavaExecutable {
    param([string]$JavaPath)
    $candidates = @()
    if ($JavaPath) {
        $candidates += Join-Path $JavaPath 'bin\java.exe'
        $candidates += $JavaPath
    }
    if ($env:JAVA_HOME) {
        $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) {
        $candidates += $command.Path
    }

    foreach ($candidate in $candidates) {
        $resolved = Resolve-AbsolutePath $candidate
        if ($resolved -and (Test-Path $resolved -PathType Leaf)) {
            return $resolved
        }
    }
    return $null
}

function Remove-ServiceIfExists {
    param([string]$ServiceName)
    $existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if (-not $existing) {
        return
    }
    Write-Host "Serviço existente encontrado: $ServiceName"
    Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 1
}

function Get-InteractiveUsernames {
    try {
        $output = quser 2>$null
    } catch {
        Write-Host 'Não foi possível executar quser; usando usuário atual'
        return @($env:USERNAME)
    }

    if (-not $output) {
        return @($env:USERNAME)
    }

    $users = @()
    foreach ($line in $output) {
        if ($line -match '^\s*USERNAME') { continue }
        $parts = $line -split '\s+' | Where-Object { $_ -ne '' }
        if ($parts.Count -ge 1) {
            $users += $parts[0]
        }
    }

    if (-not $users) {
        return @($env:USERNAME)
    }

    return $users | Select-Object -Unique
}

function Ensure-ScheduledTaskForUsers {
    param(
        [string]$ServiceBat
    )

    $users = Get-InteractiveUsernames
    $computer = $env:COMPUTERNAME

    foreach ($user in $users) {
        $sanitized = ($user -replace '[^A-Za-z0-9]', '_')
        $taskName = "NetNotifyAgent-$sanitized"
        Write-Host "Configurando tarefa agendada para $user (tarefa: $taskName)..."
        Try {
            Start-Process schtasks.exe -ArgumentList '/Delete','/TN',$taskName,'/F' -NoNewWindow -Wait -ErrorAction SilentlyContinue | Out-Null
        } catch {
            # ignore
        }
        $arguments = @(
            '/Create',
            '/SC', 'ONLOGON',
            '/RL', 'HIGHEST',
            '/F',
            '/TN', $taskName,
            '/TR', "`"$ServiceBat`"",
            '/RU', "$computer\$user"
        )
        try {
            Start-Process schtasks.exe -ArgumentList $arguments -NoNewWindow -Wait -ErrorAction Stop | Out-Null
            Write-Host "Tarefa $taskName criada para $user"
        } catch {
            Write-Host "Falha ao criar tarefa para $user: $($_.Exception.Message)"
        }
    }
}

function Ensure-Administrator {
    $isAdmin = ([Security.Principal.WindowsPrincipal]([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if ($isAdmin) {
        return $true
    }
    Write-Host "Este script requer privilégios de administrador."
    Write-Host "Reabrindo com privilégios elevados..."
    Start-Process PowerShell -ArgumentList "-ExecutionPolicy Bypass -File `"$PSCommandPath`" `"$InstallPath`" `"$JavaPath`"" -Verb RunAs
    exit 0
}

Ensure-Administrator

$ServiceName = 'NetNotifyAgent'
$DisplayName = 'NetNotify Agent'
$InstallPath = Resolve-AbsolutePath $InstallPath
if (-not $InstallPath) {
    Throw-Error "Erro: o caminho de instalação '$InstallPath' é inválido."
}

$JarFile = Get-AgentJar -BasePath $InstallPath
if ($JarFile) {
    $JarFile = Resolve-AbsolutePath $JarFile
}
$LibsDir = Join-Path $InstallPath 'libs'
$ServiceBat = Join-Path $InstallPath 'run-service.bat'

Write-Host "Instalando $DisplayName em: $InstallPath"
if (-not $JarFile) {
    Throw-Error "Erro: nenhum arquivo netnotifyagent-*.jar foi encontrado em $InstallPath"
}
if (-not (Test-Path $LibsDir)) {
    Throw-Error "Erro: diretório libs não encontrado em $InstallPath"
}

$JavaExe = Resolve-JavaExecutable -JavaPath $JavaPath
if (-not $JavaExe) {
    Throw-Error 'Erro: Java não encontrado. Defina JAVA_HOME ou informe o caminho do Java.'
}
Write-Host "Java localizado em: $JavaExe"

$ResourcesPath = Join-Path $InstallPath 'resources'
if (Test-Path $ResourcesPath) {
    Write-Host 'Aplicando permissões na pasta resources...'
    icacls "$ResourcesPath" /grant 'Users:(OI)(CI)M' /T /C | Out-Null
}

Write-Host 'Verificando e removendo serviço existente (caso exista)...'
Remove-ServiceIfExists -ServiceName $ServiceName

Write-Host 'Preparando diretório de logs...'
$LogDir = Join-Path $InstallPath 'logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$LogFile = Join-Path $LogDir 'service.log'

Write-Host 'Gerando script run-service.bat...'
$ServiceBatContent = @"
@echo off
setlocal EnableDelayedExpansion
set "JAVA_EXE=$JavaExe"
set "INSTALL_PATH=$InstallPath"
set "JAR_FILE=$JarFile"
set "LIBS_DIR=$LibsDir"
set "LOG_FILE=$LogFile"

if not exist "!JAR_FILE!" exit /b 1
if not exist "!LIBS_DIR!" exit /b 1

"!JAVA_EXE!" --module-path "!LIBS_DIR!" --add-modules javafx.controls,javafx.web -cp "!JAR_FILE!;!LIBS_DIR!\*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher >> "!LOG_FILE!" 2>&1
"@

$ServiceBatContent | Out-File -FilePath $ServiceBat -Encoding ASCII -Force

Write-Host 'Garantindo tarefas agendadas para usuários logados...'
Ensure-ScheduledTaskForUsers -ServiceBat $ServiceBat

Write-Host 'Criando serviço Windows...'
$binaryPath = "cmd.exe /c `"$ServiceBat`""
New-Service -Name $ServiceName `
    -DisplayName $DisplayName `
    -BinaryPathName $binaryPath `
    -StartupType Automatic `
    -ErrorAction Stop | Out-Null

Write-Host 'Serviço criado com sucesso.'
Write-Host 'Configurando timeout de inicialização...'
reg.exe add "HKLM\SYSTEM\CurrentControlSet\Services\$ServiceName" /v ServicesPipeTimeout /t REG_DWORD /d 300000 /f | Out-Null
Write-Host 'Adicionando descrição...'
sc.exe description $ServiceName 'Agent para receber notificações via RabbitMQ com interface JavaFX' | Out-Null
Write-Host 'Configurando política de recuperação...'
sc.exe failure $ServiceName reset= 86400 actions= restart/60000 | Out-Null

Write-Host ''
Write-Host 'Instalação concluída. O serviço NetNotifyAgent está configurado e será iniciado automaticamente na próxima reinicialização.'
Write-Host 'Para iniciar imediatamente, execute: Start-Service -Name NetNotifyAgent'
exit 0
