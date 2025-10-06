# NetNotify Agent

Agente desktop para receber e exibir notificações via RabbitMQ. Fornece integração com exchanges/filas existentes, mostra mensagens HTML (com WebView / fallback Swing) e mantém ícone na bandeja para monitoramento do serviço.

## Propósito
O NetNotify Agent foi projetado para:
- Consumir mensagens de uma fila RabbitMQ (vinculada a um exchange).
- Exibir notificações para o usuário em janelas nativas (JavaFX WebView quando disponível; JEditorPane como fallback).
- Rodar como um agente contínuo no desktop com ícone na bandeja para controle básico e status.

## Funcionalidades principais
- Consumo contínuo de mensagens RabbitMQ com reconexão automática.
- Suporte a exchange + queue + routing key configuráveis.
- Exibição de mensagens JSON mapeadas para DTO `Message` (title, content, level, type, user, timestamps).
- Renderização HTML completa via JavaFX WebView (quando JavaFX estiver disponível corretamente).
- Fallback para Swing (JEditorPane) com inlining de imagens remotos quando WebView não está disponível.
- Ícone na bandeja (TrayService) para exibir status, abrir log/config e encerrar o agente.
- Tratamento robusto de erros para evitar que exceções interrompam o loop de consumo.
- Arquivo de configuração `resources/settings.properties` (com exemplo `settings.example.properties`).

## Arquitetura / principais classes
- `Netnotifyagent` — ponto de entrada; inicializa `RabbitmqService` e `TrayService`, registra shutdown hook.
- `RabbitmqService` — encapsula conexão, factory, reconexão, validação de fila/exchange e callbacks de consumo. Mantém regras e política de retry.
- `Alert` — responsável por exibir notificações; usa JavaFX WebView quando possível; possui fallback Swing + inlining de imagens.
- `TrayService` — gerencia o ícone na bandeja e ações do usuário.
- `dto.Message` — DTO que representa o JSON de mensagens recebidas.
- `utils.Functions`, `utils.MessageUtils`, `utils.Constants` — utilitários de configuração / formatação.

## Configuração
Arquivo: `resources/settings.properties` (exemplo em `resources/settings.example.properties`)

Parâmetros importantes:
```properties
rabbitmq.host=localhost
rabbitmq.port=5672
rabbitmq.username=guest
rabbitmq.password=guest
rabbitmq.virtualhost=/
rabbitmq.exchange=<nome_do_exchange>
rabbitmq.queue=<nome_da_fila_existente>
rabbitmq.routingkey=<routing_key_ou_vazio>
```

Observações:
- Para exchanges do tipo `fanout`, a routing key é ignorada.
- O serviço verifica a fila com `queueDeclarePassive()` para não criar filas novas.

## Requisitos
- Java 17 (build compatível) — compilação e execução.
- JavaFX (controls + web) versão compatível com a JDK se usar WebView.
- RabbitMQ acessível pela rede.

## Execução (desenvolvimento)
Recomendado usar o plugin OpenJFX (pom) para montar module-path automaticamente:

- Via Maven (usa javafx-maven-plugin):
  ```
  mvn clean javafx:run -Djavafx.classifier=win
  ```
  Ajuste o classifier para `linux` ou `mac` conforme o SO.

- Via linha de comando com JavaFX SDK instalado:
  ```
  java --module-path "C:\path\to\javafx-sdk-<vers>\lib" --add-modules=javafx.controls,javafx.web -jar target/netnotifyagent-<version>.jar
  ```

- Execução direta do JAR sem JavaFX nativo resultará em erro `no jfxwebkit` ao instanciar WebView; nesse caso o agente tenta fallback para Swing.

## Empacotamento / distribuição
- Gerar JAR via Maven (`mvn package`) e distribuir junto com libs JavaFX em `libs/`, ou criar executável nativo (jlink / jpackage) incluindo runtime e bibliotecas nativas.

## Comportamento em falta de JavaFX natives
- Se `jfxwebkit` não estiver disponível, WebView lança `UnsatisfiedLinkError`.
- O projeto contém fallback que usa Swing para exibir HTML (com limitação de CSS/JS) e tenta embutir imagens remotas (data URIs) para melhor renderização.

## Logs e troubleshooting
- Erros de conexão/retry são logados no console padrão.
- Mensagens comuns:
  - `no jfxwebkit in java.library.path` → indicar que deve executar com module-path contendo libs nativas do JavaFX ou usar `mvn javafx:run`.
  - `Fila '<nome>' não existe` → verifique `rabbitmq.queue` no settings e se a fila foi criada por produtor.
- Verifique `settings.properties`, permissões de rede e compatibilidade de arquitetura (JDK x JavaFX: ambos 64-bit, por exemplo).

## Segurança e robustez
- A classe de consumo trata exceções por mensagem para não interromper o consumo.
- `RabbitmqService` usa heartbeat e recuperação automática da biblioteca RabbitMQ para reconexões.
- Sanitização de HTML é feita antes do carregamento para reduzir risco de scripts/iframes.

## Desenvolvimento
- Código Java modularizado em `src/main/java`.
- Dependências gerenciadas por Maven (pom.xml inclui RabbitMQ client, Jackson, JavaFX deps).
- Lombok pode estar presente; habilite annotation processing no seu IDE se usar.

## Contribuição
- Abrir issue descrevendo bug ou feature.
- Fork, branch com alteração e PR com descrição clara das mudanças e testes.

## Contato
Desenvolvido para uso interno DER-PB. Para ajustes de configuração/integração, abrir issue no repositório ou contatar responsável técnico.