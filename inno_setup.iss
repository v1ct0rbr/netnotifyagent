#define AppName "NetNotify Agent"
#define AppVersion "1.0.0"
#define AppPublisher "DER-PB"

[Setup]
AppName={#AppName}
AppVersion={#AppVersion}
DefaultDirName={pf64}\NetNotifyAgent
DefaultGroupName={#AppName}
OutputBaseFilename=NetNotifyAgent-Setup-{#AppVersion}
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
WizardStyle=modern
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
Name: "desktopicon"; Description: "Criar atalho na area de trabalho"; Flags: unchecked

[Run]
Filename: "{app}\run.bat"; StatusMsg: "Iniciando aplicacao para configurar auto-inicializacao..."; Flags: nowait; WorkingDir: "{app}"

[Icons]
Name: "{group}\NetNotify Agent"; Filename: "{app}\run.bat"; IconFilename: "{app}\resources\images\icon.ico"
Name: "{commondesktop}\NetNotify Agent"; Filename: "{app}\run.bat"; Tasks: desktopicon; IconFilename: "{app}\resources\images\icon.ico"

[Code]
var
	PageRabbit: TInputQueryWizardPage;
	PageJava: TInputQueryWizardPage;

procedure InitializeWizard;
begin
	{ Aumenta o tamanho da janela do instalador para caber todas as entradas }
	WizardForm.ClientWidth := ScaleX(900);
	WizardForm.ClientHeight := ScaleY(640);
	{ Ajusta os cadernos internos para usar o novo tamanho }
	WizardForm.InnerNotebook.Width := WizardForm.ClientWidth - WizardForm.InnerNotebook.Left * 2;
	WizardForm.InnerNotebook.Height := WizardForm.CancelButton.Top - WizardForm.InnerNotebook.Top - ScaleY(16);
	WizardForm.OuterNotebook.Width := WizardForm.InnerNotebook.Width;
		WizardForm.OuterNotebook.Height := WizardForm.InnerNotebook.Height;

	{ Página de entrada: Configurações RabbitMQ }
	PageRabbit := CreateInputQueryPage(wpSelectDir,
		'Configuração RabbitMQ',
		'Defina os parâmetros de conexão com RabbitMQ',
		'Esses valores serão gravados em resources\settings.properties após a instalação.');
	PageRabbit.Add('Host RabbitMQ:', False);
	PageRabbit.Add('Porta RabbitMQ:', False);
	PageRabbit.Add('Usuário RabbitMQ:', False);
	PageRabbit.Add('Senha RabbitMQ:', True);
	PageRabbit.Add('Exchange:', False);
	PageRabbit.Add('Virtual Host:', False);
	PageRabbit.Values[0] := 'localhost';
	PageRabbit.Values[1] := '5672';
	PageRabbit.Values[2] := 'admin';
	PageRabbit.Values[3] := 'admin';
	PageRabbit.Values[4] := 'netnotify';
	PageRabbit.Values[5] := '/';

	{ Página de entrada: Configurações de Java (opcional) }
	PageJava := CreateInputQueryPage(wpSelectDir,
		'Configuração Java (Opcional)',
		'Deixe em branco para usar o Java do PATH ou JAVA_HOME',
		'Se ambos forem definidos, java.executable tem prioridade.');
	PageJava.Add('java.home (ex: C:\\Program Files\\Java\\jdk-21):', False);
	PageJava.Add('java.executable (ex: C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe):', False);
	PageJava.Values[0] := '';
	PageJava.Values[1] := '';
end;

procedure WriteSettingsFile;
var
	SettingsPath: String;
	SettingsContent: String;
	CRLF: String;
begin
	SettingsPath := ExpandConstant('{app}\resources\settings.properties');
	CRLF := #13#10;

	SettingsContent := '';
	SettingsContent := SettingsContent + 'rabbitmq.host=' + PageRabbit.Values[0] + CRLF;
	SettingsContent := SettingsContent + 'rabbitmq.port=' + PageRabbit.Values[1] + CRLF;
	SettingsContent := SettingsContent + 'rabbitmq.username=' + PageRabbit.Values[2] + CRLF;
	SettingsContent := SettingsContent + 'rabbitmq.password=' + PageRabbit.Values[3] + CRLF;
	SettingsContent := SettingsContent + 'rabbitmq.exchange=' + PageRabbit.Values[4] + CRLF;
	SettingsContent := SettingsContent + 'rabbitmq.routingkey=' + CRLF;
	SettingsContent := SettingsContent + 'rabbitmq.virtualhost=' + PageRabbit.Values[5] + CRLF;
	SettingsContent := SettingsContent + CRLF;
	SettingsContent := SettingsContent + '# ===== JAVA RUNTIME OPCIONAL =====' + CRLF;
	SettingsContent := SettingsContent + '# Defina o caminho para a versao do Java a ser usada pela aplicacao.' + CRLF;
	SettingsContent := SettingsContent + '# Se ambos forem definidos, java.executable tem prioridade.' + CRLF;
	SettingsContent := SettingsContent + '# Caso nao definido, o launcher usara JAVA_HOME ou o java do PATH.' + CRLF;

	if PageJava.Values[0] <> '' then
		SettingsContent := SettingsContent + 'java.home=' + PageJava.Values[0] + CRLF
	else
		SettingsContent := SettingsContent + '# java.home=' + CRLF;

	if PageJava.Values[1] <> '' then
		SettingsContent := SettingsContent + 'java.executable=' + PageJava.Values[1] + CRLF
	else
		SettingsContent := SettingsContent + '# java.executable=' + CRLF;

	SettingsContent := SettingsContent + CRLF;
	SettingsContent := SettingsContent + '# ===== CONFIGURACOES DE FILTRO DE MENSAGENS =====' + CRLF;
	SettingsContent := SettingsContent + '# Controle quais niveis de mensagens deseja receber' + CRLF;
	SettingsContent := SettingsContent + '# Valores: true (ativado) ou false (desativado)' + CRLF;
	SettingsContent := SettingsContent + '# Padrao: todos os niveis ativados (true)' + CRLF;
	SettingsContent := SettingsContent + '# Nota: O nivel Urgente sempre permanece ativo' + CRLF + CRLF;
	SettingsContent := SettingsContent + 'filter.level.baixo.enabled=true' + CRLF;
	SettingsContent := SettingsContent + 'filter.level.normal.enabled=true' + CRLF;
	SettingsContent := SettingsContent + 'filter.level.alto.enabled=true' + CRLF;
	SettingsContent := SettingsContent + 'filter.level.urgente.enabled=true' + CRLF;

	SaveStringToFile(SettingsPath, SettingsContent, False);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
	{ Grava o arquivo depois da instalação dos arquivos }
	if CurStep = ssPostInstall then
		WriteSettingsFile;
end;
