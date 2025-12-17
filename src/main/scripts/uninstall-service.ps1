# Script de desinstalação de serviço NetNotify Agent
# Execução: PowerShell -ExecutionPolicy Bypass -File uninstall-service.ps1

param(
    [string]$ServiceName = "NetNotifyAgent"
)

function Ensure-Administrator {
    $isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if ($isAdmin) {
        return $true
    }
    Write-Host "Este script requer privilégios de administrador."
    Write-Host "Reabrindo com privilégios elevados..."
    Start-Process PowerShell -ArgumentList "-ExecutionPolicy Bypass -File `"$PSCommandPath`" `"$ServiceName`"" -Verb RunAs
    exit 0
}

function Remove-NetNotifyTasks {
    Write-Host "Removendo tarefas agendadas do NetNotify Agent..."

    try {
        schtasks.exe /Delete /TN "NetNotifyAgent-RefreshUserTasks" /F 2>$null | Out-Null
    } catch {
        # ignorar erros
    }

    try {
        $output = schtasks.exe /Query /FO LIST /V 2>$null
    } catch {
        Write-Host "Aviso: não foi possível consultar tarefas agendadas."
        return
    }

    if (-not $output) {
        return
    }

    foreach ($line in $output) {
        if ($line -match '^\s*TaskName:\s*(.+)$') {
            $name = $Matches[1].Trim()
            if ($name -like '\\NetNotifyAgent-*') {
                try {
                    schtasks.exe /Delete /TN $name /F 2>$null | Out-Null
                    Write-Host "Tarefa removida: $name"
                } catch {
                    Write-Host "Falha ao remover tarefa $name: $($_.Exception.Message)"
                }
            }
        }
    }
}

Ensure-Administrator

Write-Host ""
Write-Host "======================================"
Write-Host "Desinstalando $ServiceName"
Write-Host "======================================"
Write-Host ""

$wmiService = Get-CimInstance -ClassName Win32_Service -Filter "Name='$ServiceName'" -ErrorAction SilentlyContinue
if ($wmiService) {
    $imagePath = $wmiService.PathName
    $serviceScript = $null
    $match = [regex]::Match($imagePath, '"(?<path>[^\"]*run-service\.bat)"')
    if ($match.Success) {
        $serviceScript = $match.Groups['path'].Value
    } else {
        $match = [regex]::Match($imagePath, '(?<path>[A-Za-z]:[^\"]*run-service\.bat)')
        if ($match.Success) {
            $serviceScript = $match.Groups['path'].Value
        }
    }

    Write-Host "Parando o serviço..."
    Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1

    Write-Host "Encerrando processos Java relacionados..."
    $javaProcesses = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue
    $stoppedCount = 0
    foreach ($proc in $javaProcesses) {
        if ($proc.CommandLine -and $proc.CommandLine -imatch 'netnotifyagent') {
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
            $stoppedCount++
        }
    }
    if ($stoppedCount -gt 0) {
        Write-Host "Encerrados $stoppedCount processos Java vinculados ao Agent."
    } else {
        Write-Host "Nenhum processo Java identificado em execução para o Agent."
    }
    Start-Sleep -Seconds 1

    Write-Host "Removendo o serviço..."
    sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 1
    $deleteCode = $LASTEXITCODE
    if ($deleteCode -eq 0) {
        Write-Host "OK: Serviço removido com sucesso."
    } elseif ($deleteCode -eq 1072) {
        Write-Host "Aviso: O serviço está marcado para exclusão e será removido em seguida."
    } else {
        Write-Host "Aviso: Falha ao remover o serviço (erro $deleteCode)."
    }

    if ($serviceScript) {
        if (Test-Path $serviceScript) {
            Remove-Item -LiteralPath $serviceScript -Force -ErrorAction SilentlyContinue
            Write-Host "Script de execução removido: $serviceScript"
        }
    }

    $regPath = "HKLM:\SYSTEM\CurrentControlSet\Services\$ServiceName"
    if (Test-Path $regPath) {
        Remove-Item -Path $regPath -Force -ErrorAction SilentlyContinue
        Write-Host "Entrada do registro removida."
    }
} else {
    Write-Host "Aviso: O serviço '$ServiceName' não foi encontrado. Continuando com remoção de tarefas agendadas."
}

Remove-NetNotifyTasks

Write-Host ""
Write-Host "======================================"
Write-Host "Desinstalação concluída!"
Write-Host "======================================"
Write-Host ""

exit 0
