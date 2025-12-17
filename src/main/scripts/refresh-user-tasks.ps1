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
        Write-Host "Verificando tarefa agendada para $($user) (tarefa: $taskName)..."
        try {
            Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
        } catch {
            # ignore
        }
        try {
            $action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument "/c `"$ServiceBat`""
            $trigger = New-ScheduledTaskTrigger -AtLogOn -User "$($computer)\$user"
            $principal = New-ScheduledTaskPrincipal -UserId "$($computer)\$user" -RunLevel Highest -LogonType Interactive
            Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
            Write-Host "Tarefa $taskName criada/atualizada para $($user)"
        } catch {
            Write-Host "Falha ao criar tarefa para $($user): $($_.Exception | Select-Object -ExpandProperty Message)"
        }
    }
}

Write-Host 'Atualizando tarefas agendadas de logon para usuários atuais...'
Ensure-ScheduledTaskForUsers -ServiceBat $ServiceBat
Write-Host 'Atualização concluída.'
