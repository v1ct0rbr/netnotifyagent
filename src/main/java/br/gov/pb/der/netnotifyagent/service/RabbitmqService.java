package br.gov.pb.der.netnotifyagent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.rabbitmq.client.ConnectionFactory;

import br.gov.pb.der.netnotifyagent.utils.Functions;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RabbitmqService {

    private static final String RESOURCES_PATH = "resources/";
    private static final String SETTINGS_FILE = RESOURCES_PATH + "settings.properties";

    private String host;
    private String username;
    private String password;
    private String exchangeName;
    private String virtualHost;

    Properties settings;

    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(this.host);
        factory.setUsername(this.username);
        factory.setPassword(this.password);
        factory.setVirtualHost(this.virtualHost);
        return factory;
    }

    public RabbitmqService() {
        try {
            this.settings = Functions.loadProperties(SETTINGS_FILE);
            this.host = settings.getProperty("rabbitmq.host");
            this.username = settings.getProperty("rabbitmq.username");
            this.password = settings.getProperty("rabbitmq.password");
            this.exchangeName = settings.getProperty("rabbitmq.exchange");
            this.virtualHost = settings.getProperty("rabbitmq.virtualhost", "/");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void consumeAndShow() {
        try {
            ConnectionFactory factory = rabbitConnectionFactory();
            com.rabbitmq.client.Connection connection = factory.newConnection();
            com.rabbitmq.client.Channel channel = connection.createChannel();
            channel.exchangeDeclare(exchangeName, "fanout");
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, exchangeName, "");

            System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

            com.rabbitmq.client.DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                br.gov.pb.der.netnotifyagent.ui.Alert.getInstance().showInfo(msg);
                System.out.println(" [x] Received '" + msg + "'");
            };

            channel.basicConsume(queueName, true, deliverCallback, consumerTag1 -> {
            });

            // Mantém a thread viva para consumir continuamente
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
