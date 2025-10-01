# NetNotify Agent v${project.version}

Agent para recebimento de notificações via RabbitMQ com interface JavaFX.

## Formas de Execução

### 1. Executável Windows (Recomendado)
**Duplo clique em:** `NetNotifyAgent.exe`

### 2. JAR Executável
**Linha de comando:** `java -jar netnotifyagent-${project.version}.jar`

### 3. Com Módulos JavaFX Explícitos
```bash
java --module-path libs --add-modules javafx.controls,javafx.web -cp netnotifyagent-${project.version}.jar;libs/* br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher
```

## Requisitos do Sistema

- **Java**: versão 11 ou superior
- **Sistema Operacional**: Windows 7+ (64-bit recomendado)
- **Memória**: Mínimo 512MB RAM
- **RabbitMQ**: servidor configurado e acessível

## Estrutura dos Arquivos

```
NetNotifyAgent/
├── NetNotifyAgent.exe          # Executável Windows
├── netnotifyagent-${project.version}.jar    # JAR principal
├── libs/                       # Dependências JavaFX e outras
│   ├── javafx-controls-*.jar
│   ├── javafx-web-*.jar
│   └── ...
└── README.md                   # Este arquivo
```

## Configuração

O aplicativo procura por um arquivo `settings.properties` para configurações do RabbitMQ.
Se não encontrar, usa valores padrão (localhost:5672).

## Funcionalidades

- **Ícone na Bandeja**: Monitora conexão RabbitMQ
- **Notificações HTML**: Suporte completo a HTML/CSS
- **Imagens Remotas**: Carrega imagens via HTTP/HTTPS
- **Menu Contextual**: Clique direito no ícone para opções

## Troubleshooting

### "Aplicação não inicia"
1. Verifique se Java 11+ está instalado
2. Execute via linha de comando para ver erros
3. Certifique-se que a pasta `libs/` está presente

### "JavaFX não encontrado"
1. Verifique se todos os arquivos foram extraídos
2. Execute como administrador se necessário
3. Use o JAR com comando explícito (opção 3 acima)

### "Não conecta no RabbitMQ"
1. Verifique se RabbitMQ está executando
2. Confirme configurações de rede/firewall
3. Verifique credenciais no settings.properties

## Suporte Técnico

**Desenvolvido por:** DER-PB  
**Versão:** ${project.version}  
**Build:** ${timestamp}