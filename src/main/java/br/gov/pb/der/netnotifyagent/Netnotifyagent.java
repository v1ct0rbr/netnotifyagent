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

    public static void main(String[] args) throws Exception {
        rabbitmqService = new RabbitmqService();

        // Configuração do RabbitMQ
        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(rabbitmqService.getHost());
        factory.setUsername(rabbitmqService.getUsername());
        factory.setPassword(rabbitmqService.getPassword());

        for (;;) {

            try (com.rabbitmq.client.Connection connection = factory.newConnection();
                    com.rabbitmq.client.Channel channel = connection.createChannel()) {

                String queueName = channel.queueDeclare().getQueue();
                channel.queueBind(queueName, rabbitmqService.getExchangeName(), "");

                System.out.println("Aguardando mensagens...");

                com.rabbitmq.client.DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                    System.out.println("Mensagem recebida: " + message);
                    br.gov.pb.der.netnotifyagent.ui.Alert.getInstance().showHtml(message);

                };

                channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
                });

                // Mantém a aplicação rodando
                while (true) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.out.println("Erro na conexão com RabbitMQ: " + e.getMessage());
            }

            Thread.sleep(5000);
        }

    }

}
