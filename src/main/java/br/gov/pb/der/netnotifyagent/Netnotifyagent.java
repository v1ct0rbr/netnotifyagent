/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package br.gov.pb.der.netnotifyagent;

import java.awt.AWTException;

import br.gov.pb.der.netnotifyagent.service.RabbitmqService;
import br.gov.pb.der.netnotifyagent.ui.TrayService;

/**
 *
 * @author victorqueiroga
 */
public class Netnotifyagent {

    private static RabbitmqService rabbitmqService;

    public static void main(String[] args) {
        System.out.println("Iniciando NetNotify Agent...");

        try {
            // Inicializa o serviço RabbitMQ
            rabbitmqService = new RabbitmqService();

            // Inicializa tray icon
            final TrayService[] trayService = new TrayService[1];
            try {
                trayService[0] = new TrayService(rabbitmqService);
            } catch (AWTException awt) {
                System.err.println("Tray not available: " + awt.getMessage());
            }

            // Configura shutdown hook para parada graceful
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Finalizando aplicação...");
                if (rabbitmqService != null) {
                    rabbitmqService.stop();
                }
                if (trayService[0] != null) {
                    trayService[0].shutdown();
                }
            }));

            // Inicia o consumo de mensagens em thread separada
            Thread consumerThread = new Thread(() -> rabbitmqService.startConsuming(), "rabbitmq-consumer");
            consumerThread.setDaemon(false);
            consumerThread.start();

            // Mantém a thread main viva aguardando o consumer
            try {
                consumerThread.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            System.err.println("Erro crítico na aplicação: " + e.getMessage());
            System.exit(1);
        }
    }
}
