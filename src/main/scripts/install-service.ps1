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

function Get-ProfileUsernames {
    $profileKey = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList'
    $users = @()

    try {
        Get-ChildItem -Path $profileKey -ErrorAction Stop | ForEach-Object {
            $sidKey = $_
            try {
                $props = Get-ItemProperty -Path $sidKey.PSPath -Name 'ProfileImagePath' -ErrorAction Stop
                $profilePath = $props.ProfileImagePath
            } catch {
                return
            }

            if (-not $profilePath -or $profilePath -notlike '*\Users\*') {
                return
            }

            try {
                $sid = New-Object System.Security.Principal.SecurityIdentifier($sidKey.PSChildName)
                $ntAccount = $sid.Translate([System.Security.Principal.NTAccount])
                $account = $ntAccount.Value

                if ($account -and $account -notmatch '^(NT AUTHORITY|SYSTEM)$') {
                    $users += $account
                }
            } catch {
                # Ignora SIDs que não podem ser traduzidos
            }
        }
    } catch {
        Write-Host "Não foi possível enumerar perfis de usuário via registro: $($_.Exception.Message)"
    }

    return $users | Select-Object -Unique
}

function Get-TargetUsernames {
    # Combina usuários atualmente logados (quser) com todas as contas
    # que possuem perfil em C:\Users, evitando contas de sistema.
    $all = @()

    $interactive = Get-InteractiveUsernames
    if ($interactive) {
        $all += $interactive
    }

    $profiles = Get-ProfileUsernames
    if ($profiles) {
        $all += $profiles
    }

    if (-not $all) {
        return @($env:USERNAME)
    }

    return $all | Sort-Object -Unique
}

function Use-ScheduledTasksModule {
    try {
        Import-Module ScheduledTasks -ErrorAction Stop
        return $true
    } catch {
        Write-Host 'Módulo ScheduledTasks não disponível; usando schtasks.exe (compatível com Windows 7).'
        return $false
    }
}

function Ensure-ScheduledTaskForUsers {
    param(
        [string]$LauncherEngine,
        [string]$LauncherArgs
    )

    $users = Get-TargetUsernames

    $useScheduledModule = Use-ScheduledTasksModule

    foreach ($user in $users) {
        $sanitized = ($user -replace '[^A-Za-z0-9]', '_')
        $taskName = "NetNotifyAgent-$sanitized"

        # Usa o valor retornado pelo quser como identificador de conta.
        # Se vier "DOMINIO\\usuario", o Windows resolve para esse usuário de domínio.
        # Se vier apenas "usuario", o Windows resolve para o usuário local ou de domínio atual.
        $account = $user

        Write-Host "Configurando tarefa agendada para $($user) (tarefa: $taskName, conta: $account)..."

        if ($useScheduledModule) {
            try {
                Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
            } catch {
                # ignore
            }
            try {
                $action = New-ScheduledTaskAction -Execute $LauncherEngine -Argument $LauncherArgs
                $trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
                $principal = New-ScheduledTaskPrincipal -UserId $account -RunLevel Highest -LogonType Interactive
                Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
                Write-Host ('Tarefa {0} criada para {1} (conta: {2})' -f $taskName, $user, $account)
            } catch {
                Write-Host "Falha ao criar tarefa para $($user) (conta: $account): $($_.Exception.Message)"
            }
        } else {
            try {
                schtasks.exe /Delete /TN $taskName /F 2>$null | Out-Null
            } catch {
                # ignore
            }
            try {
                $taskCommand = "$LauncherEngine $LauncherArgs"
                schtasks.exe /Create /TN $taskName /TR $taskCommand /SC ONLOGON /RU $account /RL HIGHEST /F | Out-Null
                Write-Host ('Tarefa {0} criada (via schtasks.exe) para {1} (conta: {2})' -f $taskName, $user, $account)
            } catch {
                Write-Host "Falha ao criar tarefa (via schtasks.exe) para $($user) (conta: $account): $($_.Exception.Message)"
            }
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

    $useScheduledModule = Use-ScheduledTasksModule

    if ($useScheduledModule) {
        # Remover tarefa existente, se houver
        try {
            Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
        } catch {
            # ignore
        }

        # Ação: executar o wrapper .bat via cmd.exe, no diretório de instalação
        $action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$refreshRunner`"" -WorkingDirectory $InstallPath

        # Trigger: executar a cada 5 minutos, por um período longo (10 anos)
        $startTime = (Get-Date).AddMinutes(1)
        $interval = New-TimeSpan -Minutes 5
        $duration = [TimeSpan]::FromDays(3650)
        $trigger = New-ScheduledTaskTrigger -Once -At $startTime -RepetitionInterval $interval -RepetitionDuration $duration

        # Principal: SYSTEM, nível mais alto
        $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -RunLevel Highest

        # Configurações padrão razoáveis
        $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

        try {
            Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
            Write-Host "Tarefa periódica $taskName criada/atualizada com sucesso."
        } catch {
            Write-Host "Falha ao criar tarefa periódica ${taskName}: $($_.Exception.Message)"
        }
    } else {
        # Compatibilidade com Windows 7: usar schtasks.exe
        try {
            schtasks.exe /Delete /TN $taskName /F 2>$null | Out-Null
        } catch {
            # ignore
        }

        try {
            $taskCommand = "cmd.exe /c `"$refreshRunner`""
            schtasks.exe /Create /TN $taskName /TR $taskCommand /SC MINUTE /MO 5 /RU SYSTEM /RL HIGHEST /F | Out-Null
            Write-Host "Tarefa periódica $taskName criada/atualizada com sucesso (via schtasks.exe)."
        } catch {
            Write-Host "Falha ao criar tarefa periódica ${taskName} (via schtasks.exe): $($_.Exception.Message)"
        }
    }
}

# Configurações principais do serviço / instalação
$ServiceName = 'NetNotifyAgent'
$DisplayName = 'NetNotify Agent'

# Normaliza o caminho de instalação informado
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

$HiddenLauncher = Join-Path $InstallPath 'run-hidden.vbs'
if (-not (Test-Path $HiddenLauncher -PathType Leaf)) {
    Throw-Error "Erro: script run-hidden.vbs não encontrado em $InstallPath"
}

$TaskLauncherEngine = 'wscript.exe'
$TaskLauncherArgs = "//B //nologo `"$HiddenLauncher`""

Write-Host 'Garantindo tarefas agendadas para usuários logados...'
Ensure-ScheduledTaskForUsers -LauncherEngine $TaskLauncherEngine -LauncherArgs $TaskLauncherArgs

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
