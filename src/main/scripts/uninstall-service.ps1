# Script de desinstalação de serviço NetNotify Agent
# Execução: PowerShell -ExecutionPolicy Bypass -File uninstall-service.ps1

param(
    [string]$ServiceName = "NetNotifyAgent"
)

# Verificar se está rodando como administrador
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Este script requer privilégios de administrador."
    Write-Host "Reabrindo com privilégios elevados..."
    Start-Process PowerShell -ArgumentList "-ExecutionPolicy Bypass -File `"$PSCommandPath`" `"$ServiceName`"" -Verb RunAs
    exit 0
}

Write-Host ""
Write-Host "======================================"
Write-Host "Desinstalando $ServiceName"
Write-Host "======================================"
Write-Host ""

# Verificar se o serviço existe
$service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if (-not $service) {
    Write-Host "Aviso: O serviço '$ServiceName' não foi encontrado"
    exit 0
}

# Parar o serviço
Write-Host "Parando o serviço..."
Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

# Encerrar processos Java relacionados
Write-Host "Encerrando processos..."
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-Process -Name javaw -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

# Remover o serviço
Write-Host "Removendo o serviço..."
Remove-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

# Limpar entrada do Registro (se necessário)
Write-Host "Limpando Registro..."
$regPath = "HKLM:\SYSTEM\CurrentControlSet\Services\$ServiceName"
if (Test-Path $regPath) {
    Remove-Item -Path $regPath -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "======================================"
Write-Host "Desinstalação concluída!"
Write-Host "======================================"
Write-Host ""

exit 0
