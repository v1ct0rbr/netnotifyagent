package br.gov.pb.der.netnotifyagent.service;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Servico RabbitMQ para Agente
 */
public class RabbitmqService {

    private static final Logger logger = LoggerFactory.getLogger(RabbitmqService.class);

    private static final String RESOURCES_PATH = "resources/";
    private static final String SETTINGS_FILE = RESOURCES_PATH + "settings.properties";
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 60000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;
    private static final int CHANNEL_TIMEOUT_MS = 10000;
    private static final int AGENT_QUEUE_EXPIRES_MS = 86_400_000;
    private static final String AGENT_QUEUE_PREFIX = "queue_agent_";
    private static final String BROADCAST_ROUTING_KEY = "broadcast.general";
    private static final String DEPARTMENT_ROUTING_PREFIX = "department.";
    private static final String AGENT_ROUTING_PREFIX = "agent.";

    private static final int SUMMARY_LINE_WIDTH = 60;

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

    private String host;
    private String username;
    private String password;
    private String exchangeName;
    private String virtualHost;
    private int port;

    private String departmentName;
    private String hostname;

    private String agentQueueName;

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

    private final ExecutorService messageExecutor =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name("msg-processor").factory());

    public RabbitmqService() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            this.settings = Functions.loadProperties(SETTINGS_FILE);
            this.host = settings.getProperty("rabbitmq.host");
            this.username = settings.getProperty("rabbitmq.username");
            this.password = settings.getProperty("rabbitmq.password");
            this.exchangeName = settings.getProperty("rabbitmq.exchange", "netnotify_topic");
            this.virtualHost = settings.getProperty("rabbitmq.virtualhost", "/");
            this.port = Integer.parseInt(settings.getProperty("rabbitmq.port", "5672"));

            this.departmentName = settings.getProperty("agent.department.name", "unknown");
            this.hostname = settings.getProperty("agent.hostname", getLocalHostname());

            this.notificationsDisplayMode = settings.getProperty("notifications.display.mode", "window");

            logger.info("Configuracao carregada:");
            logger.info("  - Host: {}:{}", host, port);
            logger.info("  - Usuario: {} (agent-consumer, read-only)", username);
            logger.info("  - Exchange: {}", exchangeName);
            logger.info("  - Departamento: {}", departmentName);
            logger.info("  - Hostname: {}", hostname);
            logger.info("  - Notificacoes: {}",
                    "browser".equalsIgnoreCase(notificationsDisplayMode) ? "Browser" : "Janela padrao");

        } catch (IOException e) {
            logger.error("Erro ao carregar configuracoes: {}", e.getMessage());
        }
    }

    private String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName().replaceAll("[^a-zA-Z0-9\\-]", "_");
        } catch (UnknownHostException e) {
            return "agent-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private ConnectionFactory createConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.host);
        factory.setUsername(this.username);
        factory.setPassword(this.password);
        factory.setVirtualHost(this.virtualHost);
        factory.setPort(this.port);

        factory.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        factory.setChannelRpcTimeout(CHANNEL_TIMEOUT_MS);

        factory.setRequestedHeartbeat(HEARTBEAT_INTERVAL_MS / 1000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(RECONNECT_DELAY_MS);

        logger.debug("ConnectionFactory configurada (agent-consumer):");
        logger.debug("  - Host: {}", this.host);
        logger.debug("  - Port: {}", this.port);
        logger.debug("  - VirtualHost: {}", this.virtualHost);
        logger.debug("  - Username: {}", this.username);
        logger.debug("  - Exchange: {}", this.exchangeName);
        logger.debug("  - Permissoes: READ-ONLY (nao pode declarar/modificar)");
        logger.debug("  - Connection Timeout: {}ms", CONNECTION_TIMEOUT_MS);
        logger.debug("  - Channel RPC Timeout: {}ms", CHANNEL_TIMEOUT_MS);

        return factory;
    }

    private DeliverCallback createDeliverCallback(String queueType) {
        return (consumerTag, delivery) -> {
            final byte[] body = delivery.getBody();
            messageExecutor.submit(() -> {
                try {
                    String message = new String(body, StandardCharsets.UTF_8);
                    logger.info("[{}] Mensagem recebida: {}", queueType, message);

                    String level = extractLevelFromMessage(message);

                    if (!FilterSettings.shouldShowMessage(level)) {
                        logger.info("[RabbitmqService] Mensagem com nivel '{}' filtrada pelo usuario", level);
                        return;
                    }

                    String msgTitle = extractFieldFromMessage(message, "title");
                    if (msgTitle != null) msgTitle = Functions.stripEmoji(msgTitle);
                    br.gov.pb.der.netnotifyagent.dto.Message parsedMsg = Functions.jsonToObject(message,
                            br.gov.pb.der.netnotifyagent.dto.Message.class);
                    String htmlForHistory;
                    if (parsedMsg != null) {
                        htmlForHistory = Functions.addHtmlTagsToContent(parsedMsg);
                    } else {
                        // JSON parse failed — show only the content field if extractable, not the full JSON
                        String rawContent = extractFieldFromMessage(message, "content");
                        String display = rawContent != null ? rawContent : message;
                        htmlForHistory = "<p style=\"font-family:Arial,sans-serif;white-space:pre-wrap;word-wrap:break-word\">"
                                + Alert.sanitizeHtml(Functions.stripEmoji(display)) + "</p>";
                    }
                    NotificationHistory.getInstance().add(msgTitle, level, htmlForHistory);

                    if (isBrowserNotificationMode()) {
                        openNotificationInBrowser(message);
                    } else {
                        Alert.getInstance().showHtml(message);
                    }
                } catch (Exception e) {
                    logger.error("Erro ao processar mensagem: {}", e.getMessage());
                }
            });
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
            tempFile.toFile().deleteOnExit(); // garante limpeza ao sair
            Files.writeString(tempFile, html, StandardCharsets.UTF_8);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(tempFile.toUri());
                Thread.ofVirtual().name("temp-cleanup").start(() -> {
                    try {
                        Thread.sleep(30_000);
                        Files.deleteIfExists(tempFile);
                    } catch (Exception ignored) {}
                });
            } else {
                logger.error("Desktop browse not supported; usando janela padrao");
                Alert.getInstance().showHtml(jsonMessage);
            }
        } catch (Exception e) {
            logger.error("Erro ao abrir notificacao no navegador: {}", e.getMessage());
            Alert.getInstance().showHtml(jsonMessage);
        }
    }

    private String extractLevelFromMessage(String message) {
        try {
            ObjectMapper mapper = MAPPER;
            JsonNode jsonNode = mapper.readTree(message);
            if (jsonNode.has("level")) {
                return jsonNode.get("level").asText();
            }
        } catch (IOException e) {
            logger.info("[RabbitmqService] Aviso: nao foi possivel extrair nivel: {}", e.getMessage());
        }
        return null;
    }

    private String extractFieldFromMessage(String message, String field) {
        try {
            ObjectMapper mapper = MAPPER;
            JsonNode jsonNode = mapper.readTree(message);
            if (jsonNode.has(field)) {
                return jsonNode.get(field).asText();
            }
        } catch (IOException e) {
            logger.debug("Nao foi possivel extrair campo '{}': {}", field, e.getMessage());
        }
        return null;
    }

    private CancelCallback createCancelCallback(String queueType) {
        return consumerTag -> {
            logger.info("[{}] Consumidor cancelado: {}", queueType, consumerTag);
        };
    }

    private void setupQueueAndExchangeConsumer(Channel channel) throws IOException {

        logger.info("[SETUP] Configurando fila unica de consumo...");

        try {
            channel.exchangeDeclarePassive(exchangeName);
            logger.info("Exchange existe: {} (tipo: topic)", exchangeName);
        } catch (IOException e) {
            logger.error("ERRO: Exchange '{}' nao existe!", exchangeName);
            logger.error("  O servidor deve ter criado este exchange durante a inicializacao.");
            throw e;
        }

        this.agentQueueName = AGENT_QUEUE_PREFIX + normalizeRoutingSegment(hostname);
        Map<String, Object> queueArguments = new LinkedHashMap<>();
        queueArguments.put("x-expires", AGENT_QUEUE_EXPIRES_MS);

        try {
            channel.queueDeclare(agentQueueName, true, false, false, queueArguments);
            logger.info("Fila do agente verificada/criada: {}", agentQueueName);
        } catch (IOException e) {
            logger.error("ERRO ao declarar fila '{}': {}", agentQueueName, e.getMessage());
            throw e;
        }

        bindQueue(channel, BROADCAST_ROUTING_KEY);
        bindQueue(channel, AGENT_ROUTING_PREFIX + normalizeRoutingSegment(hostname));

        if (departmentName != null && !departmentName.isEmpty() && !departmentName.equals("unknown")) {
            bindQueue(channel, DEPARTMENT_ROUTING_PREFIX + normalizeRoutingSegment(departmentName));
        } else {
            logger.warn("AVISO: departmentName nao configurado! Agente recebera apenas broadcast e mensagens diretas.");
        }

        channel.basicConsume(agentQueueName, true,
                createDeliverCallback("AGENTE"),
                createCancelCallback("AGENTE"));
        logger.info("  -> Consumindo: SIM");
    }

    private void waitForConnection() throws InterruptedException {
        synchronized (connectionMonitor) {
            while (!shouldStop && !forceReconnect && isConnected) {
                connectionMonitor.wait(1000);
            }
        }
    }

    public void startConsuming() {
        while (!shouldStop) {
            ConnectionFactory factory = createConnectionFactory();

            try {
                status = "Connecting to " + host + ":" + port;
                logger.info("-> {}", status);

                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();

                synchronized (connectionMonitor) {
                    currentConnection = connection;
                    currentChannel = channel;
                    isConnected = true;
                    forceReconnect = false;
                }

                setupQueueAndExchangeConsumer(channel);

                status = "Connected (Queue: " + agentQueueName + ")";
                logger.info("Conectado! Aguardando mensagens...");

                waitForConnection();

            } catch (ShutdownSignalException e) {
                lastError = "Shutdown: " + e.getMessage();
                status = "Disconnected";
                logger.error("Conexao fechada: {}", e.getMessage());

            } catch (IOException e) {
                lastError = "IO: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                status = "Disconnected";
                logger.error("Erro de I/O: {}", e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("ACCESS-REFUSED")) {
                    logger.error("  -> SOLUCAO: Verifique se o usuario '{}' tem permissao READ", username);
                }

            } catch (TimeoutException e) {
                lastError = "Timeout: " + e.getMessage();
                status = "Disconnected";
                logger.error("Timeout: {}", e.getMessage());

            } catch (InterruptedException e) {
                lastError = e.getMessage();
                status = "Disconnected";
                logger.error("Erro na conexao: {}", e.getMessage());
                Thread.currentThread().interrupt();
                break;

            } finally {
                synchronized (connectionMonitor) {
                    isConnected = false;
                    if (currentChannel != null && currentChannel.isOpen()) {
                        try { currentChannel.close(); } catch (Exception ignored) {}
                    }
                    if (currentConnection != null && currentConnection.isOpen()) {
                        try { currentConnection.close(); } catch (Exception ignored) {}
                    }
                    currentChannel = null;
                    currentConnection = null;
                    connectionMonitor.notifyAll();
                }
            }

            if (!shouldStop) {
                logger.info("-> Tentando reconectar em {}s...", RECONNECT_DELAY_MS / 1000);
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    logger.info("Aplicacao interrompida.");
                    break;
                }
            }
        }

        status = "Stopped";
        logger.info("Servico RabbitMQ finalizado.");
    }

    public void stop() {
        synchronized (connectionMonitor) {
            this.shouldStop = true;
            connectionMonitor.notifyAll();
        }
        messageExecutor.shutdownNow();
        logger.info("-> Parando o servico RabbitMQ...");
    }

    public void restart() {
        logger.info("Reiniciando o servico RabbitMQ com novas configuracoes...");

        loadConfiguration();

        synchronized (connectionMonitor) {
            this.forceReconnect = true;
            connectionMonitor.notifyAll();
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (connectionMonitor) {
            this.forceReconnect = false;
        }
    }

    public String getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        String title = "--== NetNotify Agent - Status ==--";
        sb.append(Functions.headerLine('=', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.lineBuilder(title, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.divideLine('=', SUMMARY_LINE_WIDTH)).append("\n");
        String serverInfo = "RabbitMQ Server: " + host + ":" + port;
        sb.append(Functions.lineBuilder(serverInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.divideLine('=', SUMMARY_LINE_WIDTH)).append("\n");
        String userInfo = "Usuario: " + username + " (RO)";
        sb.append(Functions.lineBuilder(userInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        String exchangeInfo = "Exchange: " + exchangeName;
        sb.append(Functions.lineBuilder(exchangeInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        String queueInfo = "Fila: " + (agentQueueName != null ? agentQueueName : "nao conectada");
        sb.append(Functions.lineBuilder(queueInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.divideLine('=', SUMMARY_LINE_WIDTH)).append("\n");
        String departmentInfo = "Departamento: " + departmentName;
        sb.append(Functions.lineBuilder(departmentInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        String hostInfo = "Hostname: " + hostname;
        sb.append(Functions.lineBuilder(hostInfo, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        sb.append(Functions.divideLine('=', SUMMARY_LINE_WIDTH)).append("\n");
        String statusMsg = "Status: " + status;
        sb.append(Functions.lineBuilder(statusMsg, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        if (lastError != null && !lastError.isEmpty()) {
            String errorMsg = lastError.length() > 32 ? lastError.substring(0, 29) + "..." : lastError;
            sb.append(Functions.lineBuilder("Ultimo Erro: " + errorMsg, ' ', SUMMARY_LINE_WIDTH)).append("\n");
        }
        sb.append(Functions.footerLine('=', SUMMARY_LINE_WIDTH)).append("\n");
        return sb.toString();
    }

    // ========== GETTERS ==========
    public String getHost() { return host; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getExchangeName() { return exchangeName; }
    public String getVirtualHost() { return virtualHost; }
    public int getPort() { return port; }
    public String getDepartmentName() { return departmentName; }
    public String getHostname() { return hostname; }
    public String getAgentQueueName() { return agentQueueName; }

    public boolean isConnected() {
        return isConnected;
    }

    private void bindQueue(Channel channel, String routingKey) throws IOException {
        channel.queueBind(agentQueueName, exchangeName, routingKey);
        logger.info("  -> Binding Pattern: {}", routingKey);
    }

    private String normalizeRoutingSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Routing segment cannot be blank");
        }
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Routing segment cannot be blank");
        }
        return normalized;
    }
}
