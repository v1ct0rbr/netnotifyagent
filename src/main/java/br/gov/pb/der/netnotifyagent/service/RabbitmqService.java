package br.gov.pb.der.netnotifyagent.service;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.ShutdownSignalException;

import br.gov.pb.der.netnotifyagent.ui.Alert;
import br.gov.pb.der.netnotifyagent.utils.FilterSettings;
import br.gov.pb.der.netnotifyagent.utils.Functions;

/**
 * Serviço RabbitMQ para Agente
 * 
 * Arquitetura:
 * - Consome mensagens de 2 filas:
 * 1. Fila Geral: recebe broadcast.* (mensagens para TODOS)
 * 2. Fila Departamento: recebe department.{nome} (mensagens específicas)
 * 
 * Permissões:
 * - Usa credencial 'agent-consumer' com permissão READ-ONLY
 * - NÃO pode declarar/modificar exchanges e filas (apenas consumir)
 * - Filas são pré-criadas pelo servidor (admin-producer) na inicialização
 * 
 * Estratégia de Consumo:
 * - Declarações PASSIVAS apenas (verifica se existem, não cria)
 * - Usa bindPattern para receber mensagens via Topic Exchange
 * - Auto-reconexão com retry exponencial
 */
public class RabbitmqService {

    private static final String RESOURCES_PATH = "resources/";
    private static final String SETTINGS_FILE = RESOURCES_PATH + "settings.properties";
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 60000;
    private static final int CONNECTION_TIMEOUT_MS = 10000; // 10 segundos
    private static final int CHANNEL_TIMEOUT_MS = 10000; // 10 segundos

    private static final int SUMMARY_LINE_WIDTH = 60;

    private String host;
    private String username;
    private String password;
    private String exchangeName;
    private String virtualHost;
    private int port;

    // Informações do agente
    private String departmentName; // APENAS NOME (ex: "Financeiro", "RH")
    private String hostname; // Hostname da máquina

    // Nomes das filas criadas
    private String generalQueueName;
    private String departmentQueueName;

    // Preferência de exibição das notificações (window | browser)
    private String notificationsDisplayMode;

    private Properties settings;
    private volatile boolean shouldStop = false;
    private volatile boolean forceReconnect = false;
    private volatile String status = "Stopped";
    private volatile String lastError = "";
    private volatile boolean isConnected = false;
    private final Object connectionMonitor = new Object();
    private volatile Connection currentConnection = null;
    private volatile Channel currentChannel = null;

    /**
     * Construtor - carrega configurações de settings.properties
     */
    public RabbitmqService() {
        loadConfiguration();
    }

    /**
     * Carrega configurações de RabbitMQ e do agente do arquivo de propriedades
     */
    private void loadConfiguration() {
        try {
            this.settings = Functions.loadProperties(SETTINGS_FILE);
            this.host = settings.getProperty("rabbitmq.host");
            this.username = settings.getProperty("rabbitmq.username");
            this.password = settings.getProperty("rabbitmq.password");
            this.exchangeName = settings.getProperty("rabbitmq.exchange", "netnotify_topic");
            this.virtualHost = settings.getProperty("rabbitmq.virtualhost", "/");
            this.port = Integer.parseInt(settings.getProperty("rabbitmq.port", "5672"));

            // Carrega APENAS o NOME do departamento (não ID, não outras informações)
            this.departmentName = settings.getProperty("agent.department.name", "unknown");
            this.hostname = settings.getProperty("agent.hostname", getLocalHostname());

            this.notificationsDisplayMode = settings.getProperty("notifications.display.mode", "window");

            System.out.println("✓ Configuração carregada:");
            System.out.println("  - Host: " + host + ":" + port);
            System.out.println("  - Usuário: " + username + " (agent-consumer, read-only)");
            System.out.println("  - Exchange: " + exchangeName);
            System.out.println("  - Departamento: " + departmentName);
            System.out.println("  - Hostname: " + hostname);
            System.out.println("  - Notificações: "
                    + ("browser".equalsIgnoreCase(notificationsDisplayMode) ? "Browser" : "Janela padrão"));

        } catch (IOException e) {
            System.err.println("✗ Erro ao carregar configurações: " + e.getMessage());
        }
    }

    /**
     * Obtém hostname local ou gera ID único se não conseguir
     */
    private String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName().replaceAll("[^a-zA-Z0-9\\-]", "_");
        } catch (UnknownHostException e) {
            return "agent-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * Cria ConnectionFactory com credenciais de consumidor (agent-consumer)
     * - Usa permissões read-only
     * - NÃO pode declarar/modificar infraestrutura
     */
    private ConnectionFactory createConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.host);
        factory.setUsername(this.username); // agent-consumer
        factory.setPassword(this.password);
        factory.setVirtualHost(this.virtualHost);
        factory.setPort(this.port);

        // Configurações de timeouts (em milissegundos)
        factory.setConnectionTimeout(CONNECTION_TIMEOUT_MS); // 10 segundos para conectar
        factory.setChannelRpcTimeout(CHANNEL_TIMEOUT_MS); // 10 segundos para operações no canal

        // Configurações de resilência
        factory.setRequestedHeartbeat(HEARTBEAT_INTERVAL_MS / 1000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(RECONNECT_DELAY_MS);

        System.out.println("[DEBUG] ConnectionFactory configurada (agent-consumer):");
        System.out.println("  - Host: " + this.host);
        System.out.println("  - Port: " + this.port);
        System.out.println("  - VirtualHost: " + this.virtualHost);
        System.out.println("  - Username: " + this.username);
        System.out.println("  - Exchange: " + this.exchangeName);
        System.out.println("  - Permissões: READ-ONLY (não pode declarar/modificar)");
        System.out.println("  - Connection Timeout: " + CONNECTION_TIMEOUT_MS + "ms");
        System.out.println("  - Channel RPC Timeout: " + CHANNEL_TIMEOUT_MS + "ms");

        return factory;
    }

    /**
     * Cria callback para processar mensagens recebidas
     * 
     * @param queueType Tipo de fila ("GERAL" ou "DEPARTAMENTO") - apenas para
     *                  logging
     */
    private DeliverCallback createDeliverCallback(String queueType) {
        return (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[" + queueType + "] Mensagem recebida: " + message);

                // Extrai nível da mensagem JSON para aplicar filtro
                String level = extractLevelFromMessage(message);

                // Verifica se deve exibir baseado em filtro do usuário
                if (!FilterSettings.shouldShowMessage(level)) {
                    System.out.println("[RabbitmqService] Mensagem com nível '" + level + "' filtrada pelo usuário");
                    return;
                }

                // Exibe alerta conforme preferência do usuário
                if (isBrowserNotificationMode()) {
                    openNotificationInBrowser(message);
                } else {
                    Alert.getInstance().showHtml(message);
                }

            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem: " + e.getMessage());
            }
        };
    }

    private boolean isBrowserNotificationMode() {
        return "browser".equalsIgnoreCase(notificationsDisplayMode);
    }

    private void openNotificationInBrowser(String jsonMessage) {
        try {
            br.gov.pb.der.netnotifyagent.dto.Message msg = Functions.jsonToObject(jsonMessage,
                    br.gov.pb.der.netnotifyagent.dto.Message.class);
            if (msg == null) {
                Alert.getInstance().showHtml(jsonMessage);
                return;
            }

            String html = Functions.addHtmlTagsToContent(msg);
            Path tempFile = Files.createTempFile("netnotifyagent-msg-", ".html");
            Files.writeString(tempFile, html, StandardCharsets.UTF_8);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(tempFile.toUri());
            } else {
                System.err.println("Desktop browse not supported; usando janela padrão");
                Alert.getInstance().showHtml(jsonMessage);
            }
        } catch (Exception e) {
            System.err.println("Erro ao abrir notificação no navegador: " + e.getMessage());
            Alert.getInstance().showHtml(jsonMessage);
        }
    }

    /**
     * Extrai o campo "level" (INFO, WARN, ERROR, etc) da mensagem JSON
     * Usado para aplicar filtro de severidade
     */
    private String extractLevelFromMessage(String message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(message);
            if (jsonNode.has("level")) {
                return jsonNode.get("level").asText();
            }
        } catch (IOException e) {
            System.out.println("[RabbitmqService] Aviso: não foi possível extrair nível: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cria callback para quando o consumidor é cancelado
     */
    private CancelCallback createCancelCallback(String queueType) {
        return consumerTag -> {
            System.out.println("[" + queueType + "] Consumidor cancelado: " + consumerTag);
        };
    }

    /**
     * Configura e inicia consumo de 2 filas:
     * 
     * 1. FILA GERAL (broadcast.*)
     * - Recebe mensagens para TODOS os agentes
     * - Nome: queue_agent_{hostname}
     * - Binding Pattern: broadcast.*
     * 
     * 2. FILA DEPARTAMENTO (department.{nome})
     * - Recebe mensagens específicas do departamento deste agente
     * - Nome: queue_department_{departamento}_{hostname}
     * - Binding Pattern: department.{nome}
     * 
     * IMPORTANTE: Usa declarações ATIVAS com idempotência
     * - RabbitMQ garante que queueDeclare() cria se não existir, verifica se
     * existir
     * - Elimina race condition entre verificação e criação
     * - Permissões do usuário agent-consumer aplicadas corretamente
     * 
     * @param channel Canal aberto com RabbitMQ
     */
    private void setupQueueAndExchangeConsumer(Channel channel) throws IOException {

        System.out.println("\n[SETUP] Configurando filas de consumo...");

        // ========== VERIFICAÇÃO DO EXCHANGE ==========
        // Verifica se o exchange existe (não cria, pois é criado pelo servidor)
        try {
            channel.exchangeDeclarePassive(exchangeName);
            System.out.println("✓ Exchange existe: " + exchangeName + " (tipo: topic)");
        } catch (IOException e) {
            System.err.println("✗ ERRO: Exchange '" + exchangeName + "' não existe!");
            System.err.println("  O servidor deve ter criado este exchange durante a inicialização.");
            System.err.println("  Verifique se o servidor está rodando.");
            throw e;
        }

        // ========== FILA 1: MENSAGENS GERAIS (broadcast) ==========
        this.generalQueueName = "queue_agent_" + hostname;
        System.out.println("\n[FILA 1] Configurando fila geral...");

        try {
            // Declara a fila (cria se não existir, verifica se existir)
            // RabbitMQ garante idempotência - permissions aplicadas pelo user
            // agent-consumer
            channel.queueDeclare(generalQueueName, true, false, false, null);
            System.out.println("✓ Fila Geral verificada/criada: " + generalQueueName);
        } catch (IOException e) {
            System.err.println("✗ ERRO ao declarar fila '" + generalQueueName + "'");
            System.err.println("  Mensagem: " + e.getMessage());
            System.err.println("  Tipo: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.err.println("  Causa: " + e.getCause().getMessage());
            }
            throw e;
        }

        // Bind para receber BROADCAST de TODOS
        try {
            channel.queueBind(generalQueueName, exchangeName, "broadcast.*");
            System.out.println("  → Binding Pattern: broadcast.*");
            System.out.println("  → Receberá: mensagens gerais para TODOS os agentes");
        } catch (IOException e) {
            System.err.println("✗ ERRO ao fazer binding da fila '" + generalQueueName + "'");
            System.err.println("  Exchange: " + exchangeName);
            System.err.println("  Pattern: broadcast.*");
            System.err.println("  Mensagem: " + e.getMessage());
            throw e;
        }

        // Inicia consumo da fila geral
        channel.basicConsume(generalQueueName, true,
                createDeliverCallback("GERAL"),
                createCancelCallback("GERAL"));
        System.out.println("  → Consumindo: SIM");

        // ========== FILA 2: MENSAGENS DO DEPARTAMENTO ==========
        System.out.println("\n[FILA 2] Configurando fila de departamento...");

        if (departmentName != null && !departmentName.isEmpty() && !departmentName.equals("unknown")) {
            String deptNameFormatted = departmentName.toLowerCase().replace(" ", "_");
            this.departmentQueueName = "queue_department_" + deptNameFormatted + "_" + hostname;

            try {
                // Declara a fila (cria se não existir, verifica se existir)
                channel.queueDeclare(departmentQueueName, true, false, false, null);
                System.out.println("✓ Fila Departamento verificada/criada: " + departmentQueueName);
            } catch (IOException e) {
                System.err.println("✗ ERRO ao declarar fila '" + departmentQueueName + "'");
                System.err.println("  Mensagem: " + e.getMessage());
                throw e;
            }

            // Bind para receber mensagens ESPECÍFICAS do departamento
            String deptRoutingPattern = "department." + deptNameFormatted;
            try {
                channel.queueBind(departmentQueueName, exchangeName, deptRoutingPattern);
                System.out.println("  → Binding Pattern: " + deptRoutingPattern);
                System.out.println("  → Receberá: mensagens específicas de " + departmentName);
            } catch (IOException e) {
                System.err.println("✗ ERRO ao fazer binding da fila '" + departmentQueueName + "'");
                System.err.println("  Exchange: " + exchangeName);
                System.err.println("  Pattern: " + deptRoutingPattern);
                System.err.println("  Mensagem: " + e.getMessage());
                throw e;
            }

            // Inicia consumo da fila de departamento
            channel.basicConsume(departmentQueueName, true,
                    createDeliverCallback("DEPARTAMENTO"),
                    createCancelCallback("DEPARTAMENTO"));
            System.out.println("  → Consumindo: SIM");

        } else {
            System.out.println("⚠ AVISO: departmentName não configurado!");
            System.out.println("  Agente receberá APENAS mensagens gerais (broadcast.*)");
            this.departmentQueueName = null;
        }

        System.out.println("");
    }

    /**
     * Aguarda indefinidamente até que shouldStop seja setado como true
     * (de forma thread-safe usando monitor compartilhado)
     */
    private void waitForConnection() throws InterruptedException {
        synchronized (connectionMonitor) {
            while (!shouldStop && !forceReconnect && isConnected) {
                connectionMonitor.wait(1000);
            }
        }
    }

    /**
     * Inicia o consumo de mensagens do RabbitMQ
     * 
     * Fluxo:
     * 1. Conecta ao RabbitMQ com credenciais de consumidor (read-only)
     * 2. Configura as 2 filas (geral + departamento)
     * 3. Aguarda mensagens indefinidamente
     * 4. Se desconectar, reconecta após 5 segundos
     * 5. Continua até shouldStop = true
     */
    public void startConsuming() {
        while (!shouldStop) {
            // Recriar factory a cada tentativa para pegar configurações atualizadas
            ConnectionFactory factory = createConnectionFactory();

            try {
                status = "Connecting to " + host + ":" + port;
                System.out.println("→ " + status + "...");

                // Cria conexão
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();

                // Armazena referências
                synchronized (connectionMonitor) {
                    currentConnection = connection;
                    currentChannel = channel;
                    isConnected = true;
                    forceReconnect = false; // Reset do flag após reconectar com sucesso
                }

                setupQueueAndExchangeConsumer(channel);

                status = "Connected (General: " + generalQueueName +
                        (departmentQueueName != null ? ", Dept: " + departmentQueueName : "") + ")";
                System.out.println("✓ Conectado! Aguardando mensagens...\n");

                waitForConnection();

            } catch (ShutdownSignalException e) {
                // Desconexão controlada ou inesperada
                lastError = "Shutdown: " + e.getMessage();
                status = "Disconnected";
                System.err.println("✗ Conexão fechada: " + e.getMessage());
                if (e.getCause() != null) {
                    System.err.println("  Causa: " + e.getCause().getMessage());
                }

            } catch (IOException e) {
                // Erro de I/O (geralmente ACCESS_REFUSED = permissão negada)
                lastError = "IO: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                status = "Disconnected";
                System.err.println("✗ Erro de I/O: " + e.getMessage());
                System.err.println("  Tipo: " + e.getClass().getName());
                if (e.getCause() != null) {
                    System.err.println("  Causa: " + e.getCause().getMessage());
                }

                // Se for ACCESS_REFUSED, é problema de permissões
                if (e.getMessage() != null && e.getMessage().contains("ACCESS-REFUSED")) {
                    System.err.println("  → SOLUÇÃO: Verifique se o usuário '" + username + "' tem permissão READ");
                    System.err.println("            Usuário deve ser 'agent-consumer' com permissões read-only");
                }

            } catch (TimeoutException e) {
                // Timeout na conexão
                lastError = "Timeout: " + e.getMessage();
                status = "Disconnected";
                System.err.println("✗ Timeout: " + e.getMessage());
                if (e.getCause() != null) {
                    System.err.println("  Causa: " + e.getCause().getMessage());
                }

            } catch (InterruptedException e) {
                lastError = e.getMessage();
                status = "Disconnected";
                System.err.println("✗ Erro na conexão: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;

            } finally {
                // Limpa referências de conexão
                synchronized (connectionMonitor) {
                    isConnected = false;
                    if (currentChannel != null && currentChannel.isOpen()) {
                        try {
                            currentChannel.close();
                        } catch (Exception ignored) {
                            // ignore
                        }
                    }
                    if (currentConnection != null && currentConnection.isOpen()) {
                        try {
                            currentConnection.close();
                        } catch (Exception ignored) {
                            // ignore
                        }
                    }
                    currentChannel = null;
                    currentConnection = null;
                    connectionMonitor.notifyAll();
                }
            }

            if (!shouldStop) {
                System.out.println("→ Tentando reconectar em " + (RECONNECT_DELAY_MS / 1000) + "s...\n");
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    System.out.println("Aplicação interrompida.");
                    break;
                }
            }
        }

        status = "Stopped";
        System.out.println("✓ Serviço RabbitMQ finalizado.");
    }

    /**
     * Para o serviço de consumo (thread-safe)
     */
    public void stop() {
        synchronized (connectionMonitor) {
            this.shouldStop = true;
            connectionMonitor.notifyAll();
        }
        System.out.println("→ Parando o serviço RabbitMQ...");
    }

    /**
     * Reinicia o serviço com as configurações atualizadas
     * Recarrega o arquivo de propriedades e inicia o consumo novamente
     */
    public void restart() {
        System.out.println("↻ Reiniciando o serviço RabbitMQ com novas configurações...");

        // Recarrega configurações primeiro
        loadConfiguration();

        // Sinaliza para forçar reconexão
        synchronized (connectionMonitor) {
            this.forceReconnect = true;
            connectionMonitor.notifyAll();
        }

        // Aguarda a thread anterior processar o sinal de reconexão
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Reset do flag
        synchronized (connectionMonitor) {
            this.forceReconnect = false;
        }
    }

    /**
     * Retorna status atual da conexão
     */
    public String getStatus() {
        return status;
    }

    /**
     * Retorna último erro ocorrido
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Retorna resumo formatado compacto da configuração do agente para exibição em
     * tray/status
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        String title = "--== NetNotify Agent - Status ==--";
        sb.append(Functions.headerLine('═', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.lineBuilder(title, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.divideLine('═', SUMMARY_LINE_WIDTH)).append("\n");
        String serverInfo = "RabbitMQ Server: " + host + ":" + port;
        sb.append(Functions.lineBuilder(serverInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.divideLine('═', SUMMARY_LINE_WIDTH)).append("\n");
        String userInfo = "Usuário: " + username + " (RO)";
        sb.append(Functions.lineBuilder(userInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        String exchangeInfo = "Exchange: " + exchangeName;
        sb.append(Functions.lineBuilder(exchangeInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.divideLine('═', SUMMARY_LINE_WIDTH)).append("\n");
        String departmentInfo = "Departamento: " + departmentName;
        sb.append(Functions.lineBuilder(departmentInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        String hostInfo = "Hostname: " + hostname;
        sb.append(Functions.lineBuilder(hostInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");

        sb.append(Functions.divideLine('═', SUMMARY_LINE_WIDTH)).append("\n");

        String statusMsg = "Status: " + status;

        sb.append(Functions.lineBuilder(statusMsg, ' ', SUMMARY_LINE_WIDTH)).append("\n");

        if (lastError != null && !lastError.isEmpty()) {
            String errorMsg = lastError.length() > 32
                    ? lastError.substring(0, 29) + "..."
                    : lastError;
            String lastErrorInfo = "Último Erro: " + errorMsg;
            sb.append(Functions.lineBuilder(lastErrorInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");

        }

        sb.append(Functions.footerLine('═', SUMMARY_LINE_WIDTH)).append("\n");

        return sb.toString();
    }

    // ========== GETTERS ==========
    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public int getPort() {
        return port;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getHostname() {
        return hostname;
    }

    public String getGeneralQueueName() {
        return generalQueueName;
    }

    public String getDepartmentQueueName() {
        return departmentQueueName;
    }

    public boolean isConnected() {
        return isConnected;
    }
}
