# PowerShell: create scheduled task to run at user logon (shows UI, no console)
param(
  [string]$TaskName = "NetNotifyAgent",
  [string]$BaseDir = "$PSScriptRoot"
)

$jar = Join-Path $BaseDir "netnotifyagent-1.0-SNAPSHOT.jar"
$libs = Join-Path $BaseDir "libs"

if ($env:JAVA_HOME) {
  $javaw = Join-Path $env:JAVA_HOME "bin\javaw.exe"
} else {
  $javaw = (Get-Command javaw.exe -ErrorAction SilentlyContinue)?.Source
}
if (-not (Test-Path $javaw)) { Write-Error "javaw not found. Set JAVA_HOME or install javaw on PATH."; exit 1 }
if (-not (Test-Path $jar)) { Write-Error "JAR not found: $jar"; exit 1 }
if (-not (Test-Path $libs)) { Write-Error "libs not found: $libs"; exit 1 }

$arguments = "--module-path `"$libs`" --add-modules javafx.controls,javafx.web -cp `"$jar`;$libs\*`" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher"
$action = New-ScheduledTaskAction -Execute $javaw -Argument $arguments
$trigger = New-ScheduledTaskTrigger -AtLogOn
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -RunLevel Highest -Description "NetNotify Agent (start at logon)" -User $env:USERNAME -Force
Write-Host "Scheduled Task '$TaskName' created."