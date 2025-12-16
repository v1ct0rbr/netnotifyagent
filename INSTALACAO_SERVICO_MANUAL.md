# Instalação Manual do Serviço NetNotify Agent

Se o serviço não iniciou automaticamente durante a instalação ou se você precisa reinstalá-lo, siga os passos abaixo.

## Pré-requisitos

- Windows 10/11 ou Windows Server 2016+
- Java 17 ou superior instalado
- Privilégios de administrador
- PowerShell 3.0 ou superior (geralmente já instalado)

## Instalação do Serviço

### Opção 1: Via PowerShell (Recomendado)

1. Abra **PowerShell como Administrador**:
   - Pressione `Win + X` e clique em "Windows PowerShell (Admin)"
   - Ou procure por "PowerShell" no menu Iniciar

2. Navegue até o diretório de instalação:
   ```powershell
   cd "C:\Program Files\NetNotifyAgent"
   ```

3. Execute o script de instalação:
   ```powershell
   PowerShell -ExecutionPolicy Bypass -File install-service.ps1 "$PWD"
   ```

   Se você tem um Java específico não instalado como padrão:
   ```powershell
   PowerShell -ExecutionPolicy Bypass -File install-service.ps1 "$PWD" "C:\Program Files\Java\jdk-21"
   ```

### Opção 2: Via Batch

1. Abra **Prompt de Comando (cmd.exe) como Administrador**:
   - Pressione `Win + X` e clique em "Prompt de Comando (Admin)"

2. Navegue até o diretório:
   ```cmd
   cd "C:\Program Files\NetNotifyAgent"
   ```

3. Execute o script:
   ```cmd
   install-service.bat .
   ```

   Ou com Java específico:
   ```cmd
   install-service.bat . "C:\Program Files\Java\jdk-21"
   ```

## Iniciar o Serviço

Após a instalação, inicie o serviço com um dos seguintes comandos:

### Via PowerShell:
```powershell
Start-Service -Name NetNotifyAgent
```

### Via Prompt de Comando:
```cmd
net start NetNotifyAgent
```

### Via Services.msc:
1. Pressione `Win + R`
2. Digite `services.msc` e pressione Enter
3. Procure por "NetNotify Agent" na lista
4. Clique com botão direito e selecione "Iniciar"

## Verificar o Status

### Via PowerShell:
```powershell
Get-Service -Name NetNotifyAgent
```

### Via Prompt de Comando:
```cmd
sc.exe query NetNotifyAgent
```

### Via Services.msc:
- Abra `services.msc` e verifique a coluna "Status"

## Desinstalação do Serviço

### Via PowerShell:
```powershell
PowerShell -ExecutionPolicy Bypass -File uninstall-service.ps1 "NetNotifyAgent"
```

### Via Prompt de Comando:
```cmd
uninstall-service.bat NetNotifyAgent
```

## Solução de Problemas

### O serviço fica em "iniciando"

1. Aguarde 2-3 minutos para inicialização completa (Java + JavaFX)
2. Verifique o Event Viewer:
   - `Win + R` → `eventvwr.msc`
   - Windows Logs → System
   - Procure por erros do serviço "NetNotify Agent"

3. Verifique se Java está instalado corretamente:
   ```cmd
   java -version
   ```

### Erro de permissão

- Execute como administrador:
  ```powershell
  Run-As Administrator
  ```

### Serviço não foi encontrado

- Reinstale o serviço seguindo os passos de instalação

### JavaFX não encontrado

- Verifique se o arquivo JAR contém todas as dependências
- Certifique-se de que a pasta `libs` existe no diretório de instalação

## Dicas de Configuração

O arquivo de configuração está em:
```
C:\Program Files\NetNotifyAgent\resources\settings.properties
```

Edite este arquivo para ajustar:
- Conexão RabbitMQ
- Nome do departamento
- Caminho do Java
- Filtros de mensagens

Após editar, reinicie o serviço:
```cmd
net stop NetNotifyAgent
net start NetNotifyAgent
```

## Logs e Debugging

Para ver logs do serviço em tempo real, use PowerShell:
```powershell
Get-EventLog -LogName System -Source "NetNotify Agent" -Newest 20
```

Ou verifique Event Viewer em:
- Windows Logs → System → Procure por fonte "NetNotify Agent"
