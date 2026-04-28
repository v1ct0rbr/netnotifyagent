package br.gov.pb.der.netnotifyagent.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Utilitário para auto-configurar a aplicação no Windows
 * Cria e inicia uma Scheduled Task para auto-iniciar na próxima vez que o usuário logar
 */
public class AutoSetup {

    private static final Logger logger = LoggerFactory.getLogger(AutoSetup.class);


    private static final String TASK_NAME = "NetNotifyAgent";
    private static final String TASK_PREFIX = "NetNotifyAgent-";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String INSTALLATION_DIR = findInstallationDir();

    /**
     * Garante que a Scheduled Task existe e está configurada para iniciar na próxima vez que o usuário logar
     * Chamada automaticamente na inicialização da aplicação
     */
    public static void ensureScheduledTaskAtLogon() {
        if (!IS_WINDOWS) {
            logger.info("[AutoSetup] Não é Windows - pulando configuração de Scheduled Task");
            return;
        }

        try {
            logger.info("[AutoSetup] Iniciando verificação de Scheduled Task...");

            if (hasInstallerManagedTasks()) {
                logger.info("[AutoSetup] Tasks por usuário já existem - pulando criação da task legada");
                removeLegacyTaskIfPresent();
                return;
            }
            
            // Verificar se a task já existe
            if (taskExists()) {
                logger.info("[AutoSetup] Scheduled Task '" + TASK_NAME + "' já existe");
                return;
            }

            logger.info("[AutoSetup] Scheduled Task não encontrada - criando...");
            
            // Aguardar 3 segundos antes de criar
            waitWithMessage(3, "Preparando para criar Scheduled Task");
            
            // Criar a task
            createScheduledTask();

            logger.info("[AutoSetup] ✓ Scheduled Task configurada com sucesso para o próximo logon");
            
        } catch (Exception e) {
            // Não bloquear a inicialização da aplicação se o AutoSetup falhar
            logger.info("[AutoSetup] ⚠ Erro ao configurar Scheduled Task (aplicação continuará): " + e.getMessage());
            // Não fazer printStackTrace para não poluir logs
        }
    }

    /**
     * Verifica se a Scheduled Task já existe
     */
    private static boolean taskExists() throws Exception {
        // Usar schtasks /query para verificar se a task existe
        ProcessBuilder pb = new ProcessBuilder("schtasks", "/query", "/tn", TASK_NAME, "/fo", "list");
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        // Se exit code é 0, a task existe
        return exitCode == 0;
    }

    /**
     * Detecta se a instalação atual já usa tasks por usuário criadas pelos scripts
     * install-service.ps1 / refresh-user-tasks.ps1. Nesse caso, a task legada
     * "NetNotifyAgent" causaria inicialização duplicada no logon.
     */
    private static boolean hasInstallerManagedTasks() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("schtasks", "/query", "/fo", "csv", "/nh");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean foundManagedTask = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("\\" + TASK_PREFIX) || line.contains("\\NetNotifyAgent-RefreshUserTasks")) {
                    foundManagedTask = true;
                    break;
                }
            }
        }

        int exitCode = process.waitFor();
        return exitCode == 0 && foundManagedTask;
    }

    private static void removeLegacyTaskIfPresent() {
        try {
            if (!taskExists()) {
                return;
            }

            ProcessBuilder pb = new ProcessBuilder("schtasks", "/delete", "/tn", TASK_NAME, "/f");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[AutoSetup] schtasks delete: " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("[AutoSetup] Scheduled Task legada removida: " + TASK_NAME);
            } else {
                logger.info("[AutoSetup] Aviso: falha ao remover task legada. Exit code: " + exitCode);
            }
        } catch (Exception e) {
            logger.info("[AutoSetup] Aviso: não foi possível remover task legada: " + e.getMessage());
        }
    }

    /**
     * Cria a Scheduled Task para iniciar na próxima vez que o usuário logar
     */
    private static void createScheduledTask() throws Exception {
        logger.info("[AutoSetup] Criando task com diretório de instalação: " + INSTALLATION_DIR);
        
        // Caminho para run.bat (script launcher)
        File runBat = new File(INSTALLATION_DIR, "run.bat");
        File hiddenLauncher = new File(INSTALLATION_DIR, "run-hidden.vbs");
        
        if (!runBat.exists()) {
            // Tentar encontrar em diretórios alternativos
            File[] candidates = {
                new File(INSTALLATION_DIR, "run.bat"),
                new File(System.getProperty("user.dir"), "run.bat"),
                new File(new File(System.getProperty("user.home")), "AppData/Local/Programs/NetNotifyAgent/run.bat")
            };
            
            boolean found = false;
            for (File candidate : candidates) {
                if (candidate.exists()) {
                    runBat = candidate;
                    found = true;
                    logger.info("[AutoSetup] run.bat encontrado em: " + runBat.getAbsolutePath());
                    break;
                }
            }
            
            if (!found) {
                throw new RuntimeException("run.bat não encontrado. Procurado em: " + INSTALLATION_DIR);
            }
        }

        if (!hiddenLauncher.exists()) {
            hiddenLauncher = new File(runBat.getParentFile(), "run-hidden.vbs");
        }

        // Comando para schtasks /create
        // Preferir wscript.exe para evitar janelas de console no logon.
        String taskCommand = hiddenLauncher.exists()
                ? "wscript.exe //B //nologo \"" + hiddenLauncher.getAbsolutePath() + "\""
                : "cmd.exe /c \"" + runBat.getAbsolutePath() + "\"";

        ProcessBuilder pb = new ProcessBuilder(
            "schtasks",
            "/create",
            "/tn", TASK_NAME,
            "/tr", taskCommand,
            "/sc", "onlogon",
            "/rl", "highest",
            "/f"
        );
        
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Capturar output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[AutoSetup] schtasks: " + line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("schtasks /create falhou com exit code: " + exitCode);
        }
        
        logger.info("[AutoSetup] Scheduled Task criada com sucesso");
    }

    /**
     * Encontra o diretório de instalação da aplicação
     * Em produção, será o diretório onde run.bat está localizado
     */
    private static String findInstallationDir() {
        try {
            // Estratégia 1: Tentar obter a localização do JAR
            String jarPath = AutoSetup.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
            File jarFile = new File(jarPath);
            
            if (jarFile.isFile()) {
                String parentDir = jarFile.getParent();
                logger.info("[AutoSetup] Diretório detectado via JAR: " + parentDir);
                
                // Verificar se run.bat existe nesse diretório
                File runBat = new File(parentDir, "run.bat");
                if (runBat.exists()) {
                    return parentDir;
                }
            }
            
            // Estratégia 2: Usar o diretório de trabalho atual
            String userDir = System.getProperty("user.dir");
            logger.info("[AutoSetup] Tentando usar user.dir: " + userDir);
            
            File runBat = new File(userDir, "run.bat");
            if (runBat.exists()) {
                return userDir;
            }
            
            // Estratégia 3: Se nenhuma funcionar, retornar user.dir como fallback
            logger.info("[AutoSetup] Aviso: run.bat não encontrado, usando user.dir como fallback");
            return userDir;
            
        } catch (Exception e) {
            logger.info("[AutoSetup] Erro ao detectar diretório de instalação: " + e.getMessage());
            return System.getProperty("user.dir");
        }
    }

    /**
     * Aguarda N segundos, exibindo mensagem a cada segundo
     */
    private static void waitWithMessage(int seconds, String message) {
        for (int i = seconds; i > 0; i--) {
            logger.info("[AutoSetup] " + message + "... (" + i + "s)");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}

