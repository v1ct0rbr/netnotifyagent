/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package br.gov.pb.der.netnotifyagent;

import br.gov.pb.der.netnotifyagent.service.RabbitmqService;

/**
 *
 * @author victorqueiroga
 */

public class Netnotifyagent {

    private static RabbitmqService rabbitmqService;
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 60000; // 60 segundos

    public static void main(String[] args) throws Exception {
        rabbitmqService = new RabbitmqService();

        // Configuração do RabbitMQ
        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(rabbitmqService.getHost());
        factory.setUsername(rabbitmqService.getUsername());
        factory.setPassword(rabbitmqService.getPassword());
        factory.setRequestedHeartbeat(HEARTBEAT_INTERVAL_MS / 1000); // em segundos
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(RECONNECT_DELAY_MS);

        while (true) {
            try {
                System.out.println("Conectando ao RabbitMQ...");

                try (com.rabbitmq.client.Connection connection = factory.newConnection();
                        com.rabbitmq.client.Channel channel = connection.createChannel()) {

                    // Declara e vincula a fila
                    String queueName = channel.queueDeclare().getQueue();
                    channel.queueBind(queueName, rabbitmqService.getExchangeName(), "");

                    System.out.println("Conectado! Aguardando mensagens...");

                    // Callback para processar mensagens
                    com.rabbitmq.client.DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        try {
                            String message = new String(delivery.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                            System.out.println("Mensagem recebida: " + message);
                            br.gov.pb.der.netnotifyagent.ui.Alert.getInstance().showHtml(message);
                        } catch (Exception e) {
                            System.err.println("Erro ao processar mensagem: " + e.getMessage());
                        }
                    };

                    // Callback para cancelamento
                    com.rabbitmq.client.CancelCallback cancelCallback = consumerTag -> {
                        System.out.println("Consumidor cancelado: " + consumerTag);
                    };

                    // Inicia o consumo
                    channel.basicConsume(queueName, true, deliverCallback, cancelCallback);

                    // Mantém a conexão ativa - sem loop infinito desnecessário
                    // A conexão ficará ativa até ser fechada ou dar erro
                    Object monitor = new Object();
                    synchronized (monitor) {
                        monitor.wait(); // Aguarda indefinidamente
                    }

                } catch (com.rabbitmq.client.ShutdownSignalException e) {
                    System.err.println("Conexão fechada pelo servidor: " + e.getMessage());
                } catch (java.io.IOException e) {
                    System.err.println("Erro de I/O na conexão: " + e.getMessage());
                } catch (java.util.concurrent.TimeoutException e) {
                    System.err.println("Timeout na conexão: " + e.getMessage());
                }

            } catch (Exception e) {
                System.err.println("Erro geral na conexão com RabbitMQ: " + e.getMessage());
            }

            // Aguarda antes de tentar reconectar
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
}
