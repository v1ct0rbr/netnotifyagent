# PowerShell: create a Windows service that runs javaw (no console).
param(
  [string]$ServiceName = "NetNotifyAgent",
  [string]$DisplayName = "NetNotify Agent",
  [string]$BaseDir = "C:\Program Files\NetNotifyAgent"
)

$jar = Join-Path $BaseDir "netnotifyagent-1.0-SNAPSHOT.jar"
$libs = Join-Path $BaseDir "libs"

# choose javaw
if ($env:JAVA_HOME) {
  $javaw = Join-Path $env:JAVA_HOME "bin\javaw.exe"
} else {
  $javaw = (Get-Command javaw.exe -ErrorAction SilentlyContinue)?.Source
}
if (-not (Test-Path $javaw)) {
  Write-Error "javaw not found. Set JAVA_HOME or install javaw on PATH."
  exit 1
}

if (-not (Test-Path $jar)) { Write-Error "JAR not found: $jar"; exit 1 }
if (-not (Test-Path $libs)) { Write-Error "libs not found: $libs"; exit 1 }

$binPath = "`"$javaw`" --module-path `"$libs`" --add-modules javafx.controls,javafx.web -cp `"$jar`;$libs\*`" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher"
Write-Host "Creating service: $ServiceName"
sc.exe create $ServiceName binPath= $binPath start= auto DisplayName= "$DisplayName" obj= "NT AUTHORITY\LocalService"
if ($LASTEXITCODE -eq 0) {
  Write-Host "Service created. Starting..."
  sc.exe start $ServiceName
} else {
  Write-Error "Failed to create service (code $LASTEXITCODE)."
}