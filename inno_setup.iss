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
Source: "target\install-service.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\install-service.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\uninstall-service.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\uninstall-service.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\run.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\run.sh"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\postinstall.bat"; DestDir: "{app}"; Flags: ignoreversion


[Tasks]
Name: "desktopicon"; Description: "Criar atalho na area de trabalho"; Flags: unchecked
Name: "runapp"; Description: "Executar NetNotify Agent apos a instalacao"; Flags: unchecked

[Run]
Filename: "{app}\postinstall.bat"; Parameters: "{app}"; StatusMsg: "Finalizando instalacao..."; Flags: runhidden nowait
Filename: "{app}\install-service.bat"; Parameters: """{app}"" ""{code:GetJavaPath}"""; StatusMsg: "Instalando NetNotify Agent como servico Windows..."; Flags: runhidden waituntilterminated
Filename: "{app}\run.bat"; Description: "Executar NetNotify Agent"; StatusMsg: "Iniciando NetNotify Agent..."; Flags: postinstall nowait skipifsilent; Tasks: runapp

[UninstallRun]
Filename: "{app}\uninstall-service.bat"; Parameters: "NetNotifyAgent"; StatusMsg: "Removendo NetNotify Agent dos servicos Windows..."; Flags: runhidden nowait



[Code]
var
	PageRabbit: TInputQueryWizardPage;
	PageJava: TInputQueryWizardPage;
	PageAgent: TInputQueryWizardPage;
	JavaFound: Boolean;
	JavaVersion: String;

function DetectJavaVersion: String;
var
	JavaPath: String;
	JVersion: String;
begin
	{ Tenta detectar Java no registro do Windows }
	JavaPath := '';
	if RegQueryStringValue(HKEY_LOCAL_MACHINE, 'SOFTWARE\JavaSoft\Java Development Kit', 'CurrentVersion', JVersion) then
	begin
		if RegQueryStringValue(HKEY_LOCAL_MACHINE, 'SOFTWARE\JavaSoft\Java Development Kit\' + JVersion, 'JavaHome', JavaPath) then
		begin
			Result := JavaPath;
			Exit;
		end;
	end;
	
	Result := '';
end;

function GetJavaPath(Param: String): String;
begin
	{ Retorna o caminho do Java: preferencia para o fornecido, senao detectado }
	if PageJava.Values[0] <> '' then
		Result := PageJava.Values[0]
	else if JavaFound then
		Result := JavaVersion
	else
		Result := '';
end;

procedure ValidateJavaPath;
var
	Message: String;
begin
	{ Valida se o Java foi fornecido quando nao foi detectado }
	if (not JavaFound) and (PageJava.Values[0] = '') then
	begin
		MsgBox('Java 17 ou superior nao foi detectado no sistema.' + #13#10 + #13#10 +
			'Por favor, forneça o caminho para o Java instalado para continuar.' + #13#10 + #13#10 +
			'Exemplo: C:\Program Files\Java\jdk-21', 
			mbError, MB_OK);
		Abort;
	end;
end;

procedure InitializeWizard;
var
	JavaPathDetected: String;
begin
	{ Detecta se Java ja existe no sistema }
	JavaPathDetected := DetectJavaVersion;
	if JavaPathDetected <> '' then
	begin
		JavaFound := True;
		JavaVersion := JavaPathDetected;
	end else
	begin
		JavaFound := False;
	end;

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
	PageRabbit.Values[2] := 'agent-consumer';
	PageRabbit.Values[3] := '';
	PageRabbit.Values[4] := 'netnotify_topic';
	PageRabbit.Values[5] := '/';

	{ Página de entrada: Configurações do Agent }
	PageAgent := CreateInputQueryPage(wpSelectDir,
		'Configuração do Agent',
		'Defina os parâmetros de identificação do Agent',
		'Nome do departamento que este agent representa.');
	PageAgent.Add('Nome do Departamento (agent.department.name):', False);
	PageAgent.Values[0] := '';

	{ Página de entrada: Configurações de Java }
	if JavaFound then
	begin
		PageJava := CreateInputQueryPage(wpSelectDir,
			'Configuração Java (Opcional)',
			'Java ' + JavaVersion + ' foi detectado no sistema',
			'Deixe em branco para usar o Java detectado ou forneça outro caminho:');
		PageJava.Add('Caminho da instalação do Java (opcional):', False);
		PageJava.Values[0] := '';
	end else
	begin
		PageJava := CreateInputQueryPage(wpSelectDir,
			'Configuração Java (OBRIGATORIA)',
			'Java nao foi detectado no sistema',
			'Digite o caminho raiz da instalação do Java (ex: C:\Program Files\Java\jdk-21):');
		PageJava.Add('Caminho da instalação do Java:', False);
		PageJava.Values[0] := '';
	end;
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
	SettingsContent := SettingsContent + '# ===== CONFIGURACOES DO AGENT =====' + CRLF;
	
	if PageAgent.Values[0] <> '' then
		SettingsContent := SettingsContent + 'agent.department.name=' + PageAgent.Values[0] + CRLF
	else
		SettingsContent := SettingsContent + '# agent.department.name=' + CRLF;

	SettingsContent := SettingsContent + CRLF;
	SettingsContent := SettingsContent + '# ===== JAVA RUNTIME =====' + CRLF;
	SettingsContent := SettingsContent + '# Defina o caminho raiz para a instalacao do Java.' + CRLF;
	SettingsContent := SettingsContent + '# Caso nao definido, o launcher usara JAVA_HOME ou o java do PATH.' + CRLF;

	if PageJava.Values[0] <> '' then
		SettingsContent := SettingsContent + 'java.home=' + PageJava.Values[0] + CRLF
	else
		SettingsContent := SettingsContent + '# java.home=' + CRLF;

	SettingsContent := SettingsContent + CRLF;
	SettingsContent := SettingsContent + '# ===== CAMINHO DE INSTALACAO =====' + CRLF;
	SettingsContent := SettingsContent + '# Caminho onde a aplicacao esta instalada.' + CRLF;
	SettingsContent := SettingsContent + '# Usado pelos scripts de instalacao e pos-instalacao como fallback.' + CRLF;
	SettingsContent := SettingsContent + 'install.path=' + ExpandConstant('{app}') + CRLF;

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

function NextButtonClick(CurPageID: Integer): Boolean;
begin
	Result := True;
	{ Validar página do Java antes de prosseguir }
	if CurPageID = PageJava.ID then
		ValidateJavaPath;
end;
