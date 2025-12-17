param(
    [string]$InstallPath = $PSScriptRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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

$InstallPath = Resolve-AbsolutePath $InstallPath
if (-not $InstallPath) {
    # fallback para a pasta do script
    $InstallPath = Resolve-AbsolutePath $PSScriptRoot
    if (-not $InstallPath) {
        Write-Host "Caminho de instalação inválido: $InstallPath"
        exit 1
    }
}

$ServiceBat = Join-Path $InstallPath 'run.bat'
if (-not (Test-Path $ServiceBat -PathType Leaf)) {
    Write-Host "Script run.bat não encontrado em $ServiceBat. Encerrando."
    exit 0
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

function Use-ScheduledTasksModule {
    try {
        Import-Module ScheduledTasks -ErrorAction Stop
        return $true
    } catch {
        Write-Host 'Módulo ScheduledTasks não disponível; usando schtasks.exe (compatível com Windows 7).'
        return $false
    }
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

function Ensure-ScheduledTaskForUsers {
    param(
        [string]$ServiceBat
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

        Write-Host "Verificando tarefa agendada para $($user) (tarefa: $taskName, conta: $account)..."

        if ($useScheduledModule) {
            try {
                Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
            } catch {
                # ignore
            }
            try {
                $action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$ServiceBat`""
                $trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
                $principal = New-ScheduledTaskPrincipal -UserId $account -RunLevel Highest -LogonType Interactive
                Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
                Write-Host "Tarefa $taskName criada/atualizada para $($user) (conta: $account)"
            } catch {
                Write-Host "Falha ao criar tarefa para $($user) (conta: $account): $($_.Exception | Select-Object -ExpandProperty Message)"
            }
        } else {
            try {
                schtasks.exe /Delete /TN $taskName /F 2>$null | Out-Null
            } catch {
                # ignore
            }
            try {
                $taskCommand = "cmd.exe /c `"$ServiceBat`""
                schtasks.exe /Create /TN $taskName /TR $taskCommand /SC ONLOGON /RU $account /RL HIGHEST /F | Out-Null
                Write-Host "Tarefa $taskName criada/atualizada (via schtasks.exe) para $($user) (conta: $account)"
            } catch {
                Write-Host "Falha ao criar tarefa (via schtasks.exe) para $($user) (conta: $account): $($_.Exception | Select-Object -ExpandProperty Message)"
            }
        }
    }
}

Write-Host 'Atualizando tarefas agendadas de logon para usuários atuais...'
Ensure-ScheduledTaskForUsers -ServiceBat $ServiceBat
Write-Host 'Atualização concluída.'
