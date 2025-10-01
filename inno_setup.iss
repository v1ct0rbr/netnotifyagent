#define AppName "NetNotify Agent"
#define AppVersion "1.0.0"
#define AppPublisher "DER-PB"

[Setup]
AppName={#AppName}
AppVersion={#AppVersion}
DefaultDirName={pf}\NetNotifyAgent
DefaultGroupName={#AppName}
OutputBaseFilename=NetNotifyAgent-Setup-{#AppVersion}
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
WizardStyle=modern
; Suporte a instalação silenciosa
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
Name: "installservice"; Description: "Instalar como serviço (requer privilégios)"; GroupDescription: "Opções adicionais:"; Flags: checkedonce
Name: "createscheduledtask"; Description: "Criar Tarefa Agendada (inicia no logon do usuário)"; Flags: unchecked
Name: "desktopicon"; Description: "Criar atalho na área de trabalho"; Flags: unchecked

[Run]
; Instalar como serviço (padrão em modo silencioso ou se selecionado)
Filename: "{win}\system32\WindowsPowerShell\v1.0\powershell.exe"; \
    Parameters: "-ExecutionPolicy Bypass -NoProfile -File ""{app}\install-service.ps1"" -BaseDir ""{app}"""; \
    StatusMsg: "Instalando como serviço..."; \
    Flags: runhidden waituntilterminated; \
    Check: WizardSilent or IsTaskSelected('installservice')

; Criar scheduled task apenas se não for serviço
Filename: "{win}\system32\WindowsPowerShell\v1.0\powershell.exe"; \
    Parameters: "-ExecutionPolicy Bypass -NoProfile -File ""{app}\create-scheduled-task.ps1"" -BaseDir ""{app}"""; \
    StatusMsg: "Criando Scheduled Task (start at logon)..."; \
    Flags: runhidden waituntilterminated; \
    Check: not WizardSilent and IsTaskSelected('createscheduledtask') and not IsTaskSelected('installservice')

[Icons]
Name: "{group}\NetNotify Agent"; Filename: "{app}\run.bat"; IconFilename: "{app}\resources\images\icon.ico"
Name: "{commondesktop}\NetNotify Agent"; Filename: "{app}\run.bat"; Tasks: desktopicon; IconFilename: "{app}\resources\images\icon.ico"

[Code]
function IsTaskSelected(const Name: String): Boolean;
begin
  Result := WizardIsTaskSelected(Name);
end;