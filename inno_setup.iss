#define AppName "NetNotify Agent"
#define AppVersion "1.0.0"
#define AppPublisher "DER-PB"

[Setup]
AppName={#AppName}
AppVersion={#AppVersion}
; Use {pf64} para forçar Program Files (não Program Files (x86))
DefaultDirName={pf64}\NetNotifyAgent
DefaultGroupName={#AppName}
OutputBaseFilename=NetNotifyAgent-Setup-{#AppVersion}
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
WizardStyle=modern
; Força arquitetura 64-bit
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
UsePreviousAppDir=yes


[Files]
Source: "target\netnotifyagent-1.0-SNAPSHOT.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\libs\*"; DestDir: "{app}\libs"; Flags: recursesubdirs ignoreversion createallsubdirs
Source: "target\resources\images\icon.ico"; DestDir: "{app}\resources\images"; Flags: ignoreversion
Source: "target\resources\settings.properties"; DestDir: "{app}\resources"; Flags: ignoreversion
Source: "target\install-service.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\create-scheduled-task.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\run.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\run.sh"; DestDir: "{app}"; Flags: ignoreversion

[Tasks]
Name: "desktopicon"; Description: "Criar atalho na área de trabalho"; Flags: unchecked

[Run]
; Executar a aplicação após a instalação para que o AutoSetup crie a Scheduled Task
; (removido da seção [Icons] e adicionado aqui para garantir execução pós-instalação)
Filename: "{app}\run.bat"; \
    StatusMsg: "Iniciando aplicação para configurar auto-inicialização..."; \
    Flags: nowait; \
    WorkingDir: "{app}"

[Icons]
Name: "{group}\NetNotify Agent"; Filename: "{app}\run.bat"; IconFilename: "{app}\resources\images\icon.ico"
Name: "{commondesktop}\NetNotify Agent"; Filename: "{app}\run.bat"; Tasks: desktopicon; IconFilename: "{app}\resources\images\icon.ico"

[Code]
function IsTaskSelected(const Name: String): Boolean;
begin
  Result := WizardIsTaskSelected(Name);
end;