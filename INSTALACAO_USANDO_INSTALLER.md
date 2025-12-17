# Instalação do NetNotify Agent usando o instalador (Inno Setup)

Este manual descreve o processo de instalação do **NetNotify Agent** utilizando o instalador gerado pelo **Inno Setup** (`NetNotifyAgent-Setup-1.0.0.exe`).

O instalador foi preparado para:
- Copiar todos os arquivos necessários do Agent para o diretório de instalação.
- Criar atalhos no menu Iniciar e (opcionalmente) na área de trabalho.
- Configurar automaticamente o serviço/tarefas agendadas do NetNotify Agent no Windows.
- Gerar o arquivo `resources/settings.properties` com as configurações informadas durante o wizard.

---

## 1. Pré‑requisitos

- **Sistema operacional**: Windows 10 ou superior, 64 bits.
- **Permissões**: Usuário com privilégios de **administrador** (o instalador requer elevação).
- **Java**:
  - Recomendado: **JDK 17 ou superior**, 64 bits.
  - Se o Java já estiver instalado e configurado no registro do Windows, o instalador tenta detectá‑lo automaticamente.
  - Caso não seja detectado, será solicitado o **caminho da instalação do Java** (ex.: `C:\Program Files\Java\jdk-21`).
- **RabbitMQ** acessível pela rede, com as credenciais e parâmetros de conexão que serão informados no wizard.

Antes de iniciar, tenha em mãos:
- Host, porta, usuário, senha, exchange e virtual host do seu servidor RabbitMQ.
- Nome do departamento que este Agent irá representar.

---

## 2. Obtendo o instalador

O instalador é gerado a partir do script `inno_setup.iss` e normalmente terá o nome:

- `NetNotifyAgent-Setup-1.0.0.exe`

Esse arquivo costuma ser disponibilizado em um pacote de distribuição (por exemplo, pasta `Output/` ou anexo em sistema interno). Copie o instalador para a máquina onde o Agent será executado.

---

## 3. Executando o instalador

1. Localize o arquivo `NetNotifyAgent-Setup-1.0.0.exe` na máquina.
2. Clique com o botão direito e selecione **"Executar como administrador"**.
3. O assistente de instalação do NetNotify Agent será aberto.

Siga as telas do wizard:

### 3.1. Tela de boas‑vindas

- Confirme que se trata do **NetNotify Agent** (publicado por **DER-PB**).
- Clique em **Avançar**.

### 3.2. Diretório de instalação

- Diretório padrão sugerido: `C:\Program Files\NetNotifyAgent` (ou equivalente para sistemas em português).
- Altere apenas se houver uma política interna específica.
- Clique em **Avançar**.

### 3.3. Configuração RabbitMQ

Nesta tela, informe os parâmetros de conexão que serão gravados no `settings.properties`:

- **Host RabbitMQ**: endereço do servidor (ex.: `localhost` ou `rabbitmq.servidor.interno`).
- **Porta RabbitMQ**: porta de conexão (padrão: `5672`).
- **Usuário RabbitMQ**: usuário que possui permissão de consumo na fila.
- **Senha RabbitMQ**: senha do usuário informado.
- **Exchange**: exchange do RabbitMQ que será utilizada pelo Agent (ex.: `netnotify_topic`).
- **Virtual Host**: vhost configurado no servidor (ex.: `/`).

Esses dados serão gravados nas chaves:

```properties
rabbitmq.host
rabbitmq.port
rabbitmq.username
rabbitmq.password
rabbitmq.exchange
rabbitmq.virtualhost
```

Clique em **Avançar**.

### 3.4. Configuração do Agent (Departamento)

Informe os dados de identificação do Agent:

- **Nome do Departamento (agent.department.name)**: texto que identifica o departamento atendido por este Agent (ex.: `Atendimento DER - Sede`).

Este valor será gravado como:

```properties
agent.department.name=<nome_informado>
```

Clique em **Avançar**.

### 3.5. Configuração do Java

O instalador tenta detectar automaticamente uma instalação de Java no Windows:

- Se **Java for detectado**:
  - A tela informa o caminho encontrado.
  - Você pode **deixar em branco** para usar o Java detectado, ou informar outro caminho manualmente (caso possua JDK específico para o Agent).

- Se **Java não for detectado**:
  - A tela será marcada como **obrigatória**.
  - Informe o **caminho raiz da instalação do Java**, por exemplo:
    - `C:\Program Files\Java\jdk-21`

Esse valor (quando fornecido) será gravado em:

```properties
java.home=<caminho_java>
```

Clique em **Avançar** após preencher.

### 3.6. Tarefas opcionais

O instalador oferece algumas tarefas adicionais:

- **Criar atalho na área de trabalho** (`desktopicon`):
  - Marca um atalho "NetNotify Agent" na área de trabalho do Windows.
- **Executar NetNotify Agent após a instalação** (`runapp`):
  - Inicia o Agent automaticamente ao concluir o wizard.

Selecione as opções desejadas e clique em **Avançar**.

### 3.7. Confirmação e cópia de arquivos

- Revise o resumo da instalação.
- Clique em **Instalar**.
- Os arquivos serão copiados para o diretório escolhido e o arquivo `resources/settings.properties` será gerado conforme os dados informados.

---

## 4. Configuração automática de serviço/tarefas

Ao final da cópia de arquivos, o instalador executa automaticamente os scripts de pós‑instalação:

- `install-service.bat`
- `install-service.ps1`
- Outros scripts auxiliares relacionados a tarefas agendadas/serviço.

Esses scripts ficam no diretório de instalação do aplicativo (por padrão, `C:\Program Files\NetNotifyAgent`). A função deles é:

- Registrar o NetNotify Agent como **serviço** ou **tarefas agendadas** no Windows.
- Garantir que o Agent seja iniciado automaticamente conforme a política definida no projeto.

> Para detalhes mais avançados sobre instalação como serviço ou ajustes manuais, consulte também:
> - `INSTALACAO_COMO_SERVICO.md`
> - `INSTALACAO_SERVICO_MANUAL.md`

Ao concluir o wizard, clique em **Concluir**.

---

## 5. Verificando a instalação

Após a instalação:

1. **Arquivos**
   - Verifique se a pasta de instalação (ex.: `C:\Program Files\NetNotifyAgent`) contém:
     - `netnotifyagent-1.0-SNAPSHOT.jar`
     - Pasta `libs` com dependências.
     - Pasta `resources` com `settings.properties` e `images`.
     - Scripts `install-service.*`, `uninstall-service.*`, `run.bat`, etc.

2. **Atalho / Execução manual**
   - Use o atalho criado no menu Iniciar ou na área de trabalho.
   - Ou execute manualmente `run.bat` na pasta de instalação (preferencialmente como usuário que irá usar o Agent).

3. **Ícone na bandeja do sistema**
   - Ao iniciar o NetNotify Agent, um ícone deve aparecer na **bandeja do sistema** (System Tray).
   - A partir dele é possível verificar status e encerrar o Agent.

4. **Conectividade RabbitMQ**
   - Verifique se o Agent consegue consumir mensagens da fila configurada.
   - Em caso de problemas, revise os parâmetros de `settings.properties` e a conectividade de rede.

---

## 6. Atualização / Reinstalação

Para atualizar para uma nova versão:

1. Obtenha o novo instalador (por exemplo, `NetNotifyAgent-Setup-<nova_versao>.exe`).
2. Execute como administrador.
3. Utilize o mesmo diretório de instalação anterior.
4. Revise as configurações de RabbitMQ e Agent durante o wizard.

O instalador utiliza a opção **UsePreviousAppDir**, o que facilita manter o mesmo diretório de instalação em reinstalações.

---

## 7. Desinstalação

Para remover o NetNotify Agent:

1. Abra o **Painel de Controle → Programas e Recursos**.
2. Localize **NetNotify Agent** na lista.
3. Clique em **Desinstalar** e siga o assistente.

Durante a desinstalação, serão executados scripts que removem o serviço/tarefas agendadas do Windows (por exemplo, `uninstall-service.bat`).

Após a remoção, verifique se a pasta de instalação foi excluída. Caso permaneça algum arquivo de configuração que você deseje preservar (como `settings.properties`), faça backup antes de apagar manualmente.

---

## 8. Problemas comuns

- **"Java 17 ou superior não foi detectado"**:
  - Certifique‑se de que o Java está instalado.
  - Na tela de Configuração Java, informe o caminho correto (ex.: `C:\Program Files\Java\jdk-21`).

- **Falha de conexão com RabbitMQ**:
  - Revise os parâmetros `rabbitmq.host`, `rabbitmq.port`, `rabbitmq.username`, `rabbitmq.password`, `rabbitmq.exchange` e `rabbitmq.virtualhost` no arquivo `settings.properties`.
  - Verifique firewall, VPN e permissões no servidor RabbitMQ.

- **Agent não inicia automaticamente após reiniciar o Windows**:
  - Verifique se o serviço/tarefas agendadas foram criados corretamente (conforme descrito em `INSTALACAO_COMO_SERVICO.md`).
  - Se necessário, execute novamente o script `install-service.bat` a partir do diretório de instalação.

Se os problemas persistirem, consulte os demais arquivos de documentação do projeto ou entre em contato com o responsável técnico pelo NetNotify Agent no DER-PB.
