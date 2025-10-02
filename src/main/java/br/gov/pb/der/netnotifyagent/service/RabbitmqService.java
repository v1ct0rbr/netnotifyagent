package br.gov.pb.der.netnotifyagent.service;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.ShutdownSignalException;

import br.gov.pb.der.netnotifyagent.ui.Alert;
import br.gov.pb.der.netnotifyagent.utils.Functions;

public class RabbitmqService {

    private static final String RESOURCES_PATH = "resources/";
    private static final String SETTINGS_FILE = RESOURCES_PATH + "settings.properties";
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 60000;

    private String host;
    private String username;
    private String password;
    private String exchangeName;
    private String queueName;
    private String routingKey;
    private String virtualHost;
    private int port;
    private Properties settings;
    private volatile boolean shouldStop = false;
    // status para exibir no tray
    private volatile String status = "Stopped";
    private volatile String lastError = "";

    public RabbitmqService() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            this.settings = Functions.loadProperties(SETTINGS_FILE);
            this.host = settings.getProperty("rabbitmq.host");
            this.username = settings.getProperty("rabbitmq.username");
            this.password = settings.getProperty("rabbitmq.password");
            this.exchangeName = settings.getProperty("rabbitmq.exchange");
            this.routingKey = settings.getProperty("rabbitmq.routingkey");
            this.virtualHost = settings.getProperty("rabbitmq.virtualhost", "/");
            this.port = Integer.parseInt(settings.getProperty("rabbitmq.port", "5672"));
        } catch (IOException e) {
            System.err.println("Erro ao carregar configurações: " + e.getMessage());
        }
    }

    private ConnectionFactory createConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.host);
        factory.setUsername(this.username);
        factory.setPassword(this.password);
        factory.setVirtualHost(this.virtualHost);
        factory.setPort(this.port);
        
        factory.setRequestedHeartbeat(HEARTBEAT_INTERVAL_MS / 1000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(RECONNECT_DELAY_MS);
        return factory;
    }

    private DeliverCallback createDeliverCallback() {
        return (consumerTag, delivery) -> {
            try {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("Mensagem recebida: " + message);
                Alert.getInstance().showHtml(message);
            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem: " + e.getMessage());
            }
        };
    }

    private CancelCallback createCancelCallback() {
        return consumerTag -> {
            System.out.println("Consumidor cancelado: " + consumerTag);
        };
    }

    private void setupQueueAndExchangeConsumer(Channel channel) throws IOException {
        // cria uma fila por instância com nome único (prefixo configurado ou netnotify)
        String hostname = "unknown-host";
        try {
            hostname = InetAddress.getLocalHost().getHostName().replaceAll("[^a-zA-Z0-9\\-]", "_");
        } catch (Exception ignored) {
        }

        String instanceQueue;
        if (queueName != null && !queueName.trim().isEmpty()) {
            instanceQueue = queueName + "." + UUID.randomUUID().toString();
        } else {
            instanceQueue = "netnotify." + hostname + "." + UUID.randomUUID().toString();
        }

        // Parâmetros: durable=false, exclusive=false, autoDelete=true
        // autoDelete=true remove a fila quando o consumidor desconectar (evita filas órfãs)
        channel.queueDeclare(instanceQueue, false, false, true, null);
        System.out.println("Criada/using fila de instancia: " + instanceQueue);

        // Se houver exchange configurado, declara (fanout por padrão) e vincula a fila
        if (exchangeName != null && !exchangeName.trim().isEmpty()) {
            String ex = exchangeName.trim();
            System.out.println("Tentando declarar/vincular exchange: '" + ex + "' (routingKey='" + (routingKey==null?"":routingKey) + "')");
            try {
                // Primeiro tenta verificar se o exchange já existe (passive) para ter melhor diagnóstico
                try {
                    channel.exchangeDeclarePassive(ex);
                    System.out.println("Exchange existe (passive): " + ex);
                } catch (IOException passiveEx) {
                    // Se exchange não existir ou houver mismatch, tenta declarar (fallback)
                    System.out.println("Exchange não encontrado/passive falhou - tentando declarar: " + ex);
                    channel.exchangeDeclare(ex, BuiltinExchangeType.FANOUT, true);
                    System.out.println("Exchange declarado: " + ex);
                }

                // bind sem routing key para fanout (usa routingKey se informado)
                String rk = (routingKey != null) ? routingKey : "";
                channel.queueBind(instanceQueue, ex, rk);
                System.out.println("Fila de instancia vinculada ao exchange: " + ex + " (rk='" + rk + "')");
            } catch (Exception e) {
                System.err.println("Erro ao declarar/vincular exchange: " + e.getMessage());
                e.printStackTrace(System.err);
                // Não interrompe o consumo da fila local — apenas registra o erro
            }
        } else {
            System.out.println("Nenhum exchange configurado; consumindo diretamente da fila de instancia.");
        }

        // Inicia o consumo na fila de instancia
        channel.basicConsume(instanceQueue, true, createDeliverCallback(), createCancelCallback());

        // Atualiza campo para relatório (opcional)
        this.queueName = instanceQueue;
    }

    private void waitForConnection() throws InterruptedException {
        Object monitor = new Object();
        synchronized (monitor) {
            while (!shouldStop) {
                monitor.wait(1000); // Verifica a cada segundo se deve parar
            }
        }
    }

    public void startConsuming() {
        ConnectionFactory factory = createConnectionFactory();

        while (!shouldStop) {
            try {
                status = "Connecting to " + host + ":" + port;
                System.out.println(status + "...");

                try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {

                    setupQueueAndExchangeConsumer(channel);
                    status = "Connected to queue: " + queueName;
                    System.out.println("Conectado! Aguardando mensagens da fila: " + queueName);

                    waitForConnection();

                } catch (ShutdownSignalException e) {
                    lastError = "Shutdown: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("Conexão fechada pelo servidor: " + e.getMessage());
                } catch (IOException e) {
                    lastError = "IO: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("Erro de I/O na conexão: " + e.getMessage());
                } catch (TimeoutException e) {
                    lastError = "Timeout: " + e.getMessage();
                    status = "Disconnected";
                    System.err.println("Timeout na conexão: " + e.getMessage());
                }

            } catch (Exception e) {
                lastError = e.getMessage();
                status = "Disconnected";
                System.err.println("Erro geral na conexão com RabbitMQ: " + e.getMessage());
            }

            if (!shouldStop) {
                System.out.println("Tentando reconectar em " + (RECONNECT_DELAY_MS / 1000) + " segundos...");
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    System.out.println("Aplicação interrompida.");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        status = "Stopped";
        System.out.println("Serviço RabbitMQ finalizado.");
    }

    public void stop() {
        this.shouldStop = true;
        System.out.println("Parando o serviço RabbitMQ...");
    }

    // getters para status
    public String getStatus() {
        return status;
    }

    public String getLastError() {
        return lastError;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Host: ").append(host).append('\n');
        sb.append("Port: ").append(port).append('\n');
        sb.append("Queue: ").append(queueName).append('\n');
        sb.append("Exchange: ").append(exchangeName != null ? exchangeName : "-").append('\n');
        sb.append("Status: ").append(status);
        if (lastError != null && !lastError.isEmpty()) {
            sb.append("\nLast error: ").append(lastError);
        }
        return sb.toString();
    }

    // Getters para compatibilidade
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

    public String getQueueName() {
        return queueName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public int getPort() {
        return port;
    }
}
