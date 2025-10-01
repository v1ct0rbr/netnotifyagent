package br.gov.pb.der.netnotifyagent;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher inteligente que configura JavaFX automaticamente
 * para permitir execução via duplo clique e linha de comando
 */
public class NetnotifyagentLauncher {
    
    private static final String[] REQUIRED_JAVAFX_MODULES = {
        "javafx.controls", "javafx.web", "javafx.base", "javafx.graphics"
    };
    
    public static void main(String[] args) {
        System.out.println("=== NetNotify Agent Launcher ===");
        
        try {
            // Detectar contexto de execução
            detectExecutionContext();
            
            // Configurar JavaFX
            setupJavaFX();
            
            // Inicializar JavaFX Platform
            initializeJavaFXPlatform();
            
            System.out.println("Iniciando aplicação principal...");
            
            // Executar aplicação principal
            Netnotifyagent.main(args);
            
        } catch (Exception e) {
            handleLaunchError(e);
        }
    }
    
    private static void detectExecutionContext() {
        String command = System.getProperty("sun.java.command", "");
        String classpath = System.getProperty("java.class.path", "");
        
        System.out.println("Comando: " + command);
        System.out.println("Contexto de execução detectado: " + 
            (command.endsWith(".jar") || command.endsWith(".exe") ? "Duplo clique" : "Linha de comando"));
        
        // Debug info
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java home: " + System.getProperty("java.home"));
        System.out.println("Working dir: " + System.getProperty("user.dir"));
    }
    
    private static void setupJavaFX() throws Exception {
        // Verificar se JavaFX já está disponível
        if (isJavaFXAvailable()) {
            System.out.println("JavaFX já disponível no classpath");
            return;
        }
        
        System.out.println("JavaFX não encontrado - Configurando...");
        
        // Encontrar diretório libs
        File libsDir = findLibsDirectory();
        if (libsDir == null) {
            throw new RuntimeException("Diretório 'libs' não encontrado. " +
                "Certifique-se de que as dependências JavaFX estão disponíveis.");
        }
        
        System.out.println("Diretório libs encontrado: " + libsDir.getAbsolutePath());
        
        // Configurar module-path
        setupModulePath(libsDir);
        
        // Adicionar JARs ao classpath dinamicamente
        addJavaFXToClasspath(libsDir);
    }
    
    private static boolean isJavaFXAvailable() {
        try {
            Class.forName("javafx.application.Platform");
            Class.forName("javafx.scene.web.WebView");
            Class.forName("javafx.scene.control.Alert");
            System.out.println("Verificação JavaFX: OK");
            return true;
        } catch (ClassNotFoundException e) {
            System.out.println("Verificação JavaFX: JavaFX não encontrado - " + e.getMessage());
            return false;
        }
    }
    
    private static File findLibsDirectory() {
        // Estratégias para encontrar o diretório libs
        List<String> searchPaths = new ArrayList<>();
        
        // 1. Relativo ao JAR atual
        try {
            String jarPath = NetnotifyagentLauncher.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
            File jarFile = new File(jarPath);
            File jarDir = jarFile.getParentFile();
            searchPaths.add(new File(jarDir, "libs").getPath());
        } catch (Exception e) {
            System.out.println("Não foi possível determinar localização do JAR: " + e.getMessage());
        }
        
        // 2. Diretório de trabalho atual
        searchPaths.add("libs");
        searchPaths.add("./libs");
        searchPaths.add("../libs");
        
        // 3. Diretório target (para desenvolvimento)
        searchPaths.add("target/libs");
        searchPaths.add("target/dependency");
        
        for (String path : searchPaths) {
            File dir = new File(path);
            System.out.println("Procurando libs em: " + dir.getAbsolutePath());
            if (dir.exists() && dir.isDirectory()) {
                File[] javaFxJars = dir.listFiles((d, name) -> 
                    name.toLowerCase().contains("javafx") && name.endsWith(".jar"));
                if (javaFxJars != null && javaFxJars.length > 0) {
                    System.out.println("JavaFX JARs encontrados: " + javaFxJars.length);
                    return dir;
                }
            }
        }
        
        return null;
    }
    
    private static void setupModulePath(File libsDir) {
        String currentModulePath = System.getProperty("java.module.path", "");
        String newModulePath = libsDir.getAbsolutePath();
        
        if (!currentModulePath.isEmpty()) {
            newModulePath = currentModulePath + File.pathSeparator + newModulePath;
        }
        
        System.setProperty("java.module.path", newModulePath);
        System.out.println("Module path configurado: " + newModulePath);
    }
    
    private static void addJavaFXToClasspath(File libsDir) throws Exception {
        File[] jarFiles = libsDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".jar"));
        
        if (jarFiles == null || jarFiles.length == 0) {
            throw new RuntimeException("Nenhum JAR encontrado no diretório libs");
        }
        
        // Adicionar JARs ao classpath usando reflection
        URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addURLMethod.setAccessible(true);
        
        int addedCount = 0;
        for (File jarFile : jarFiles) {
            try {
                addURLMethod.invoke(systemClassLoader, jarFile.toURI().toURL());
                System.out.println("Adicionado ao classpath: " + jarFile.getName());
                addedCount++;
            } catch (Exception e) {
                System.out.println("Aviso: Não foi possível adicionar " + jarFile.getName() + 
                    " ao classpath: " + e.getMessage());
            }
        }
        
        System.out.println("Total de JARs adicionados ao classpath: " + addedCount);
        
        // Verificar novamente se JavaFX está disponível
        if (!isJavaFXAvailable()) {
            throw new RuntimeException("JavaFX ainda não está disponível após configuração do classpath");
        }
    }
    
    private static void initializeJavaFXPlatform() throws Exception {
        try {
            System.out.println("Inicializando JavaFX Platform...");
            
            Class<?> platformClass = Class.forName("javafx.application.Platform");
            
            // Verificar se Platform já foi inicializado
            Method isImplicitExitMethod = platformClass.getMethod("isImplicitExit");
            boolean isInitialized = true;
            try {
                isImplicitExitMethod.invoke(null);
            } catch (Exception e) {
                isInitialized = false;
            }
            
            if (!isInitialized) {
                // Platform.startup(() -> {})
                Method startupMethod = platformClass.getMethod("startup", Runnable.class);
                startupMethod.invoke(null, (Runnable) () -> {
                    System.out.println("JavaFX Platform inicializado pelo launcher");
                });
                
                // Aguardar um pouco para garantir inicialização
                Thread.sleep(500);
            }
            
            // Platform.setImplicitExit(false)
            Method setImplicitExitMethod = platformClass.getMethod("setImplicitExit", boolean.class);
            setImplicitExitMethod.invoke(null, false);
            
            System.out.println("JavaFX Platform configurado com sucesso");
            
        } catch (Exception e) {
            throw new RuntimeException("Falha ao inicializar JavaFX Platform", e);
        }
    }
    
    private static void handleLaunchError(Exception e) {
        System.err.println("=== ERRO CRÍTICO ===");
        System.err.println("Falha ao inicializar NetNotify Agent");
        System.err.println("Erro: " + e.getMessage());
        e.printStackTrace();
        
        // Tentar mostrar dialog de erro
        try {
            showErrorDialog("Erro ao Inicializar NetNotify Agent", 
                "Não foi possível inicializar a aplicação.\n\n" +
                "Possíveis causas:\n" +
                "• Java 11+ não está instalado\n" +
                "• Dependências JavaFX não encontradas na pasta 'libs'\n" +
                "• Problema de permissões\n\n" +
                "Detalhes técnicos:\n" + e.getMessage());
        } catch (Exception dialogError) {
            System.err.println("Não foi possível mostrar dialog de erro: " + dialogError.getMessage());
        }
        
        System.exit(1);
    }
    
    private static void showErrorDialog(String title, String message) {
        try {
            // Tentar usar JOptionPane como fallback
            javax.swing.JOptionPane.showMessageDialog(null, message, title, 
                javax.swing.JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            // Se não conseguir mostrar dialog, pelo menos imprimir
            System.err.println("ERRO: " + title);
            System.err.println(message);
        }
    }
}
