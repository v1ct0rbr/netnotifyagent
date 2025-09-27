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

    public static void main(String[] args) {
        System.out.println("Iniciando NetNotify Agent...");

        try {
            // Inicializa o serviço RabbitMQ
            rabbitmqService = new RabbitmqService();

            // Configura shutdown hook para parada graceful
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Finalizando aplicação...");
                if (rabbitmqService != null) {
                    rabbitmqService.stop();
                }
            }));

            // Inicia o consumo de mensagens (método bloqueante)
            rabbitmqService.startConsuming();

        } catch (Exception e) {
            System.err.println("Erro crítico na aplicação: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
