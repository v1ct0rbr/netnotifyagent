# Instalação do NetNotify Agent como Serviço Windows

## ✅ Alterações Realizadas

O instalador foi modificado para criar automaticamente o **NetNotify Agent como um Serviço Windows** que:

- ✅ Inicia automaticamente com o Windows
- ✅ Executa para **TODOS os usuários** (não apenas um)
- ✅ Roda sem interface de console
- ✅ Usa a interface JavaFX quando necessário
- ✅ Reinicia automaticamente em caso de falha

## 📝 O que mudou no instalador

### Antes:
```
[Run]
Filename: "{app}\postinstall.bat"; ...
Filename: "{app}\run.bat"; ...  (apenas executava uma vez)
```

### Depois:
```
[Run]
Filename: "{app}\postinstall.bat"; ...
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File "{app}\install-service.ps1" -BaseDir "{app}""; ...
```

## 🔧 Processo de Instalação

Quando o usuário executar o instalador:

1. **Wizard do Inno Setup** - Configurações do RabbitMQ, Agent e Java
2. **Cópia de arquivos** - JAR, libs, scripts e recursos
3. **postinstall.bat** - Configura permissões de escrita
4. **install-service.ps1** - Cria o serviço Windows automaticamente
5. **Fim da instalação**

## 📋 Pré-requisitos

- Windows 7 ou superior (com suporte a Serviços)
- PowerShell 5.1 ou superior
- Privilégios de Administrador durante a instalação
- Java (configurado em JAVA_HOME ou PATH)

## 🚀 Compilar o novo instalador

Use o script de compilação:

```bash
mvn clean package
```

Ou manualmente:

```batch
build.bat
```

O arquivo compilado será: `Output\NetNotifyAgent-Setup-1.0.0.exe`

## 📦 Instalação Silent (sem interface)

```batch
install-silent.bat
```

O serviço será criado automaticamente mesmo na instalação silent.

## ✨ Gerenciamento do Serviço

Após a instalação, o serviço pode ser gerenciado através de:

### PowerShell (como Admin):
```powershell
# Ver status
Get-Service -Name NetNotifyAgent

# Parar o serviço
Stop-Service -Name NetNotifyAgent

# Iniciar o serviço
Start-Service -Name NetNotifyAgent

# Remover o serviço
sc.exe delete NetNotifyAgent
```

### Services.msc (GUI):
1. Pressione `Win + R`
2. Digite `services.msc`
3. Procure por "NetNotify Agent"

## 🔄 Comportamento do Serviço

- **Tipo**: Windows Service
- **Startup Type**: Automatic
- **Recovery**: Reinicia em caso de falha (3 tentativas)
- **Usuário**: Sistema Local (SYSTEM)
- **Interface**: JavaFX (quando necessário, via java.awt)

## 📊 Logs

Os logs do serviço podem ser verificados em:
- **Event Viewer** > Windows Logs > Application

## ⚠️ Notas Importantes

1. O serviço usa `javaw.exe` (sem console), então não haverá janela de console visível
2. A interface JavaFX será exibida quando alertas forem recebidos
3. Se o Java não estiver configurado em `JAVA_HOME`, certifique-se de que está no PATH
4. O script detecta e usa a primeira instância de `javaw.exe` encontrada

## 🐛 Troubleshooting

Se o serviço não iniciar:

1. Verifique se o Java está instalado:
```powershell
java -version
```

2. Verifique o log de eventos do Windows para erros

3. Execute o PowerShell como Admin e veja os detalhes:
```powershell
Get-EventLog -LogName Application -Source NetNotifyAgent -Newest 10
```

4. Verifique se os arquivos estão em `Program Files\NetNotifyAgent`:
   - netnotifyagent-1.0-SNAPSHOT.jar
   - libs\ (pasta com dependências)
   - resources\ (pasta com settings.properties)
   - install-service.ps1

---

**Data**: Dezembro 2025  
**Versão**: 1.0.0
