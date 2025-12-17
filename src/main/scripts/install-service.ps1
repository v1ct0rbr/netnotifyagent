# Script de instalação de serviço NetNotify Agent
# Execução: PowerShell -ExecutionPolicy Bypass -File install-service.ps1 "C:\caminho\instalacao"

param(
    [string]$InstallPath = (Get-Location).Path,
    [string]$JavaPath = "",
    [switch]$Pause
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

function Get-JavaHomeFromSettings {
    param([string]$BasePath)

    $settingsFile = Join-Path $BasePath 'resources\settings.properties'
    if (-not (Test-Path $settingsFile -PathType Leaf)) {
        return $null
    }

    $javaHome = $null
    foreach ($line in Get-Content -LiteralPath $settingsFile -ErrorAction SilentlyContinue) {
        if (-not $line) { continue }
        $line = $line.Trim()
        if ($line.StartsWith('#')) { continue }
        if ($line.StartsWith('java.home=')) {
            $value = $line.Substring('java.home='.Length).Trim()
            if ($value) {
                $javaHome = $value
            }
            break
        }
    }

    return $javaHome
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
        # Se for um caminho completo para o executável, use diretamente
        $candidates += $JavaPath
        # Se for um diretório de instalação, tente compor bin\java.exe de forma segura
        try {
            $candidates += (Join-Path -Path $JavaPath -ChildPath 'bin\java.exe' -ErrorAction Stop)
        } catch {
            # Caminho inválido (por exemplo, unidade inexistente); ignora e segue para próximos candidatos
        }
    }
    if ($env:JAVA_HOME) {
        try {
            $candidates += (Join-Path -Path $env:JAVA_HOME -ChildPath 'bin\java.exe' -ErrorAction Stop)
        } catch {
            # Ignora erros de unidade inexistente ou caminho inválido
        }
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
            $userName = $parts[0].TrimStart('>')
            if ($userName) {
                $users += $userName
            }
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

    Import-Module ScheduledTasks -ErrorAction Stop

    $users = Get-InteractiveUsernames
    $computer = $env:COMPUTERNAME

    foreach ($user in $users) {
        $sanitized = ($user -replace '[^A-Za-z0-9]', '_')
        $taskName = "NetNotifyAgent-$sanitized"
        Write-Host "Configurando tarefa agendada para $($user) (tarefa: $taskName)..."
        Try {
            Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
        } catch {
            # ignore
        }
        try {
            $action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$ServiceBat`""
            $trigger = New-ScheduledTaskTrigger -AtLogOn -User "$($computer)\$user"
            $principal = New-ScheduledTaskPrincipal -UserId "$($computer)\$user" -RunLevel Highest -LogonType Interactive
            Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
            Write-Host ('Tarefa {0} criada para {1}' -f $taskName, $user)
        } catch {
            Write-Host "Falha ao criar tarefa para $($user): $($_.Exception.Message)"
        }
    }
}

function Ensure-PeriodicRefreshTask {
    param(
        [string]$InstallPath
    )

    $taskName = 'NetNotifyAgent-RefreshUserTasks'
    $refreshScript = Join-Path $InstallPath 'refresh-user-tasks.ps1'
    $refreshRunner = Join-Path $InstallPath 'run-refresh-user-tasks.bat'

    if (-not (Test-Path $refreshScript -PathType Leaf)) {
        Write-Host "Script de atualização de tarefas de usuário não encontrado em $refreshScript. Ignorando criação da tarefa periódica."
        return
    }

    if (-not (Test-Path $refreshRunner -PathType Leaf)) {
        Write-Host "Wrapper run-refresh-user-tasks.bat não encontrado em $refreshRunner. Ignorando criação da tarefa periódica."
        return
    }

    Write-Host "Configurando tarefa agendada periódica ($taskName) para atualizar tarefas de logon dos usuários..."

    try {
        schtasks.exe /Delete /TN $taskName /F 2>$null | Out-Null
    } catch {
        # ignore
    }

    $tr = "`"$refreshRunner`""

    $arguments = @(
        '/Create',
        '/SC','MINUTE',
        '/MO','10',
        '/RL','HIGHEST',
        '/RU','SYSTEM',
        '/F',
        '/TN',$taskName,
        '/TR',$tr
    )

    try {
        schtasks.exe @arguments | Out-Null
        Write-Host "Tarefa periódica $taskName criada/atualizada com sucesso."
    } catch {
        Write-Host "Falha ao criar tarefa periódica ${taskName}: $($_.Exception.Message)"
    }
}

function Ensure-Administrator {
    $isAdmin = ([Security.Principal.WindowsPrincipal]([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if ($isAdmin) {
        return $true
    }
    Write-Host "Este script requer privilégios de administrador."
    Write-Host "Reabrindo com privilégios elevados..."

    $args = @('-ExecutionPolicy','Bypass','-File',"`"$PSCommandPath`"", "`"$InstallPath`"", "`"$JavaPath`"")
    if ($Pause) {
        $args += '-Pause'
    }

    Start-Process PowerShell -ArgumentList $args -Verb RunAs
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

Write-Host "Instalando $DisplayName em: $InstallPath"
if (-not $JarFile) {
    Throw-Error "Erro: nenhum arquivo netnotifyagent-*.jar foi encontrado em $InstallPath"
}
if (-not (Test-Path $LibsDir)) {
    Throw-Error "Erro: diretório libs não encontrado em $InstallPath"
}

$ResourcesPath = Join-Path $InstallPath 'resources'
if (Test-Path $ResourcesPath) {
    Write-Host 'Aplicando permissões na pasta resources...'
    icacls "$ResourcesPath" /grant "*S-1-5-32-545:(OI)(CI)M" /T /C | Out-Null
}

Write-Host 'Verificando e removendo serviço existente (caso exista)...'
Remove-ServiceIfExists -ServiceName $ServiceName

$RunBat = Join-Path $InstallPath 'run.bat'
if (-not (Test-Path $RunBat -PathType Leaf)) {
    Throw-Error "Erro: script run.bat não encontrado em $InstallPath"
}

Write-Host 'Garantindo tarefas agendadas para usuários logados...'
Ensure-ScheduledTaskForUsers -ServiceBat $RunBat

Write-Host 'Configurando tarefa periódica para atualização de tarefas de usuário...'
Ensure-PeriodicRefreshTask -InstallPath $InstallPath
Write-Host ''
Write-Host 'Instalação concluída.'
Write-Host 'As tarefas agendadas do NetNotify Agent foram configuradas.'
Write-Host 'O agente será executado automaticamente no logon dos usuários configurados.'
if ($Pause) {
    Write-Host ''
    Read-Host 'Pressione Enter para sair...'
}

exit 0
