package br.gov.pb.der.netnotifyagent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

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
            this.queueName = settings.getProperty("rabbitmq.queue");
            this.routingKey = settings.getProperty("rabbitmq.routingkey", "");
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
        // Verifica se a fila existe (sem criar)
        try {
            channel.queueDeclarePassive(queueName);
            System.out.println("Conectado à fila existente: " + queueName);
        } catch (IOException e) {
            throw new IOException("Fila '" + queueName + "' não existe: " + e.getMessage());
        }

        // Verifica se o exchange existe (opcional - pode não existir se for default)
        if (exchangeName != null && !exchangeName.isEmpty() && !exchangeName.equals("")) {
            try {
                channel.exchangeDeclarePassive(exchangeName);
                System.out.println("Exchange encontrado: " + exchangeName);

                // Vincula a fila ao exchange com routing key
                channel.queueBind(queueName, exchangeName, routingKey);
                System.out.println("Fila vinculada ao exchange com routing key: " + routingKey);
            } catch (IOException e) {
                System.out.println("Exchange não encontrado ou erro na vinculação: " + e.getMessage());
            }
        }

        // Inicia o consumo da fila existente
        channel.basicConsume(queueName, true, createDeliverCallback(), createCancelCallback());
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
                System.out.println("Conectando ao RabbitMQ em " + host + ":" + port + "...");

                try (Connection connection = factory.newConnection();
                        Channel channel = connection.createChannel()) {

                    setupQueueAndExchangeConsumer(channel);
                    System.out.println("Conectado! Aguardando mensagens da fila: " + queueName);

                    waitForConnection();

                } catch (ShutdownSignalException e) {
                    System.err.println("Conexão fechada pelo servidor: " + e.getMessage());
                } catch (IOException e) {
                    System.err.println("Erro de I/O na conexão: " + e.getMessage());
                } catch (TimeoutException e) {
                    System.err.println("Timeout na conexão: " + e.getMessage());
                }

            } catch (Exception e) {
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

        System.out.println("Serviço RabbitMQ finalizado.");
    }

    public void stop() {
        this.shouldStop = true;
        System.out.println("Parando o serviço RabbitMQ...");
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
