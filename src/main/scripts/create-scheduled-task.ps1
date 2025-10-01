# PowerShell: create scheduled task to run at user logon (shows UI, no console)
param(
    [string]$TaskName = "NetNotifyAgent",
    [string]$BaseDir = $PSScriptRoot
)

Write-Host "=== NetNotify Agent Scheduled Task Creator ==="
Write-Host "BaseDir: $BaseDir"
Write-Host "TaskName: $TaskName"

$jar = Join-Path $BaseDir "netnotifyagent-1.0-SNAPSHOT.jar"
$libs = Join-Path $BaseDir "libs"

# choose javaw (compativel com PowerShell 5.1)
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

# Remover tarefa existente se houver
try {
    $existingTask = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($existingTask) {
        Write-Host "Removendo tarefa agendada existente..."
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Start-Sleep -Seconds 1
    }
} catch {
    Write-Host "Nenhuma tarefa existente encontrada."
}

# Criar argumentos para o javaw
$arguments = "--module-path `"$libs`" --add-modules javafx.controls,javafx.web -cp `"$jar`;$libs\*`" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher"

Write-Host "Criando tarefa agendada: $TaskName"
Write-Host "Executavel: $javaw"
Write-Host "Argumentos: $arguments"

try {
    # Criar acao (compatibilidade PowerShell 5.1)
    $action = New-ScheduledTaskAction -Execute $javaw -Argument $arguments -WorkingDirectory $BaseDir
    
    # Criar trigger (ao fazer logon do usuario atual)
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    
    # Configuracoes da tarefa
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RunOnlyIfNetworkAvailable:$false
    
    # Criar principal (usuario atual)
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Highest
    
    # Registrar a tarefa
    $task = Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description "NetNotify Agent (start at logon)" -Force
    
    if ($task) {
        Write-Host "Tarefa agendada criada com sucesso!"
        
        # Verificar se foi criada
        $verifyTask = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
        if ($verifyTask) {
            Write-Host "Status da tarefa: $($verifyTask.State)"
            
            # Tentar executar uma vez para teste (opcional)
            Write-Host "Testando execucao da tarefa..."
            try {
                Start-ScheduledTask -TaskName $TaskName
                Write-Host "Tarefa iniciada com sucesso!"
                Write-Host "O NetNotify Agent deve aparecer na bandeja do sistema."
            } catch {
                Write-Warning "Falha ao iniciar a tarefa: $($_.Exception.Message)"
            }
        } else {
            Write-Error "Tarefa nao foi encontrada apos criacao!"
        }
    } else {
        Write-Error "Falha ao criar a tarefa agendada!"
    }
} catch {
    Write-Error "Erro ao criar tarefa agendada: $($_.Exception.Message)"
    exit 1
}

Write-Host ""
Write-Host "=== Criacao da Tarefa Agendada Concluida ==="
Write-Host "Para verificar: Get-ScheduledTask -TaskName $TaskName"
Write-Host "Para executar: Start-ScheduledTask -TaskName $TaskName"
Write-Host "Para remover: Unregister-ScheduledTask -TaskName $TaskName -Confirm:`$false"
Write-Host ""
Write-Host "A tarefa sera executada automaticamente quando voce fizer logon no Windows."