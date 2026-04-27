/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package br.gov.pb.der.netnotifyagent;

import java.awt.AWTException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pb.der.netnotifyagent.service.RabbitmqService;
import br.gov.pb.der.netnotifyagent.ui.FxJavaInitializer;
import br.gov.pb.der.netnotifyagent.ui.TrayService;
import br.gov.pb.der.netnotifyagent.utils.SingleInstanceLock;

/**
 *
 * @author victorqueiroga
 */
public class Netnotifyagent {

    private static final Logger logger = LoggerFactory.getLogger(Netnotifyagent.class);
    private static RabbitmqService rabbitmqService;

    public static void main(String[] args) {
        if (!SingleInstanceLock.acquire()) {
            logger.info("NetNotify Agent ja está em execução. Encerrando.");
            return;
        }

        logger.info("Iniciando NetNotify Agent...");

        try {
            // Inicializa o serviço RabbitMQ
            rabbitmqService = new RabbitmqService();

            // Inicializa o toolkit JavaFX na thread principal antes de criar a tray.
            // Isso evita deadlocks ao chamar Platform.startup() pela AWT EDT mais tarde.
            FxJavaInitializer.ensureInitialized();

            // Inicializa tray icon
            final TrayService[] trayService = new TrayService[1];
            try {
                trayService[0] = new TrayService(rabbitmqService);
            } catch (AWTException awt) {
                logger.error("Tray not available: {}", awt.getMessage());
            }

            // Configura shutdown hook para parada graceful
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Finalizando aplicação...");
                if (rabbitmqService != null) {
                    rabbitmqService.stop();
                }
                if (trayService[0] != null) {
                    trayService[0].shutdown();
                }
            }));

            // Inicia o consumo de mensagens em thread separada
            Thread consumerThread = Thread.ofPlatform()
                    .name("rabbitmq-consumer")
                    .daemon(false)
                    .unstarted(() -> rabbitmqService.startConsuming());
            consumerThread.start();

            // Mantém a thread main viva aguardando o consumer
            try {
                consumerThread.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

        } catch (Exception e) {
            logger.error("Erro crítico na aplicação: {}", e.getMessage());
            System.exit(1);
        }
    }
}
