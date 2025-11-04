package br.gov.pb.der.netnotifyagent.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Utilitário para auto-configurar a aplicação no Windows
 * Cria e inicia uma Scheduled Task para auto-iniciar na próxima vez que o usuário logar
 */
public class AutoSetup {

    private static final String TASK_NAME = "NetNotifyAgent";
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String INSTALLATION_DIR = findInstallationDir();

    /**
     * Garante que a Scheduled Task existe e está configurada para iniciar na próxima vez que o usuário logar
     * Chamada automaticamente na inicialização da aplicação
     */
    public static void ensureScheduledTaskAtLogon() {
        if (!IS_WINDOWS) {
            System.out.println("[AutoSetup] Não é Windows - pulando configuração de Scheduled Task");
            return;
        }

        try {
            System.out.println("[AutoSetup] Iniciando verificação de Scheduled Task...");
            
            // Verificar se a task já existe
            if (taskExists()) {
                System.out.println("[AutoSetup] Scheduled Task '" + TASK_NAME + "' já existe");
                return;
            }

            System.out.println("[AutoSetup] Scheduled Task não encontrada - criando...");
            
            // Aguardar 3 segundos antes de criar
            waitWithMessage(3, "Preparando para criar Scheduled Task");
            
            // Criar a task
            createScheduledTask();
            
            // Aguardar 3 segundos após criação
            waitWithMessage(3, "Iniciando Scheduled Task");
            
            // Iniciar a task imediatamente
            startScheduledTask();
            
            System.out.println("[AutoSetup] ✓ Scheduled Task configurada e iniciada com sucesso");
            
        } catch (Exception e) {
            // Não bloquear a inicialização da aplicação se o AutoSetup falhar
            System.out.println("[AutoSetup] ⚠ Erro ao configurar Scheduled Task (aplicação continuará): " + e.getMessage());
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
     * Cria a Scheduled Task para iniciar na próxima vez que o usuário logar
     */
    private static void createScheduledTask() throws Exception {
        System.out.println("[AutoSetup] Criando task com diretório de instalação: " + INSTALLATION_DIR);
        
        // Caminho para run.bat (script launcher)
        File runBat = new File(INSTALLATION_DIR, "run.bat");
        
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
                    System.out.println("[AutoSetup] run.bat encontrado em: " + runBat.getAbsolutePath());
                    break;
                }
            }
            
            if (!found) {
                throw new RuntimeException("run.bat não encontrado. Procurado em: " + INSTALLATION_DIR);
            }
        }

        // Comando para schtasks /create
        // Usar ComSpec /c para executar o run.bat
        ProcessBuilder pb = new ProcessBuilder(
            "schtasks",
            "/create",
            "/tn", TASK_NAME,
            "/tr", "cmd.exe /c \"" + runBat.getAbsolutePath() + "\"",
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
                System.out.println("[AutoSetup] schtasks: " + line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("schtasks /create falhou com exit code: " + exitCode);
        }
        
        System.out.println("[AutoSetup] Scheduled Task criada com sucesso");
    }

    /**
     * Inicia a Scheduled Task imediatamente
     */
    private static void startScheduledTask() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("schtasks", "/run", "/tn", TASK_NAME);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Capturar output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[AutoSetup] schtasks run: " + line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0 && exitCode != 1) {
            // Ignorar exit code 1 pois pode significar que a task já está em execução
            System.out.println("[AutoSetup] Aviso: schtasks /run retornou exit code: " + exitCode);
        }
        
        System.out.println("[AutoSetup] Scheduled Task iniciada");
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
                System.out.println("[AutoSetup] Diretório detectado via JAR: " + parentDir);
                
                // Verificar se run.bat existe nesse diretório
                File runBat = new File(parentDir, "run.bat");
                if (runBat.exists()) {
                    return parentDir;
                }
            }
            
            // Estratégia 2: Usar o diretório de trabalho atual
            String userDir = System.getProperty("user.dir");
            System.out.println("[AutoSetup] Tentando usar user.dir: " + userDir);
            
            File runBat = new File(userDir, "run.bat");
            if (runBat.exists()) {
                return userDir;
            }
            
            // Estratégia 3: Se nenhuma funcionar, retornar user.dir como fallback
            System.out.println("[AutoSetup] Aviso: run.bat não encontrado, usando user.dir como fallback");
            return userDir;
            
        } catch (Exception e) {
            System.out.println("[AutoSetup] Erro ao detectar diretório de instalação: " + e.getMessage());
            return System.getProperty("user.dir");
        }
    }

    /**
     * Aguarda N segundos, exibindo mensagem a cada segundo
     */
    private static void waitWithMessage(int seconds, String message) {
        for (int i = seconds; i > 0; i--) {
            System.out.println("[AutoSetup] " + message + "... (" + i + "s)");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
