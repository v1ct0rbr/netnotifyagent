package br.gov.pb.der.netnotifyagent;

import java.awt.HeadlessException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import br.gov.pb.der.netnotifyagent.utils.AutoSetup;
import br.gov.pb.der.netnotifyagent.utils.Constants;

/**
 * Launcher inteligente que configura JavaFX automaticamente
 * para permitir execução via duplo clique e linha de comando
 */
public class NetnotifyagentLauncher {

    /*
     * private static final String[] REQUIRED_JAVAFX_MODULES = {
     * "javafx.controls", "javafx.web", "javafx.base", "javafx.graphics"
     * };
     */
    public static void main(String[] args) {
        System.out.println("=== NetNotify Agent Launcher ===");

        try {
            // Validar configuração de Java antes de tudo
            validateJavaConfiguration();

            // Auto-configurar a aplicação no Windows (Scheduled Task para auto-iniciar)
            // Isso é feito ANTES de qualquer inicialização do JavaFX
            AutoSetup.ensureScheduledTaskAtLogon();

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

    private static void validateJavaConfiguration() {
        try {
            Properties settings = loadSettings();
            String configuredJavaHome = settings.getProperty("java.home", "").trim();

            if (!configuredJavaHome.isEmpty()) {
                // Remover aspas se existirem
                if (configuredJavaHome.startsWith("\"") && configuredJavaHome.endsWith("\"")) {
                    configuredJavaHome = configuredJavaHome.substring(1, configuredJavaHome.length() - 1);
                }

                File javaHomeDir = new File(configuredJavaHome);
                File javaExe = new File(javaHomeDir, "bin" + File.separator + "java.exe");
                File javaUnix = new File(javaHomeDir, "bin" + File.separator + "java");

                System.out.println("Java configurado em settings.properties: " + configuredJavaHome);

                if (!javaHomeDir.exists()) {
                    System.err.println("AVISO: Diretório java.home configurado não existe: " + configuredJavaHome);
                    System.err.println("Usando Java atual do sistema: " + System.getProperty("java.home"));
                } else if (!javaExe.exists() && !javaUnix.exists()) {
                    System.err.println(
                            "AVISO: Executável Java não encontrado em: " + configuredJavaHome + File.separator + "bin");
                    System.err.println("Usando Java atual do sistema: " + System.getProperty("java.home"));
                } else {
                    System.out.println("Java configurado validado com sucesso");
                }
            } else {
                System.out.println("Nenhuma configuração de java.home encontrada. Usando Java do sistema.");
            }
        } catch (Exception e) {
            System.err.println("AVISO ao validar configuração de Java: " + e.getMessage());
            System.out.println("Continuando com Java do sistema: " + System.getProperty("java.home"));
        }
    }

    private static Properties loadSettings() {
        Properties props = new Properties();
        try {
            String settingsPath = Constants.SETTINGS_FILE_PATH;
            File settingsFile = new File(settingsPath);

            if (settingsFile.exists()) {
                try (FileInputStream fis = new FileInputStream(settingsFile)) {
                    props.load(fis);
                    System.out.println("Configurações carregadas de: " + settingsPath);
                }
            } else {
                System.out.println("Arquivo de configurações não encontrado em: " + settingsPath);
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar configurações: " + e.getMessage());
        }
        return props;
    }

    private static void detectExecutionContext() {
        String command = System.getProperty("sun.java.command", "");

        System.out.println("Comando: " + command);
        System.out.println("Contexto de execução detectado: " +
                (command.endsWith(".jar") || command.endsWith(".exe") ? "Duplo clique" : "Linha de comando"));

        // Debug info
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java home: " + System.getProperty("java.home"));
        System.out.println("Working dir: " + System.getProperty("user.dir"));
    }

    private static void setupJavaFX() throws Exception {
        // Encontrar diretório libs PRIMEIRO
        File libsDir = findLibsDirectory();
        if (libsDir == null) {
            throw new RuntimeException("Diretório 'libs' não encontrado. " +
                    "Certifique-se de que as dependências JavaFX estão disponíveis.");
        }

        System.out.println("Diretório libs encontrado: " + libsDir.getAbsolutePath());

        // CRUCIAL: Extrair e carregar natives (.dll) ANTES de carregar classes JavaFX
        System.out.println("Extraindo e carregando natives do JavaFX...");
        extractAndLoadJavaFXNatives(libsDir);

        // Configurar module-path
        setupModulePath(libsDir);

        // Adicionar JARs ao classpath dinamicamente
        addJavaFXToClasspath(libsDir);

        // Verificar se JavaFX já está disponível
        if (isJavaFXAvailable()) {
            System.out.println("JavaFX já disponível no classpath");
        }
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
                File[] javaFxJars = dir
                        .listFiles((d, name) -> name.toLowerCase().contains("javafx") && name.endsWith(".jar"));
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
        File[] jarFiles = libsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            throw new RuntimeException("Nenhum JAR encontrado no diretório libs");
        }

        // Em Java 17+, não podemos usar URLClassLoader.addURL() diretamente
        // Os JARs já estão no classpath via Maven Manifest
        // O importante é que os natives foram extraídos e adicionados ao
        // java.library.path
        System.out.println("JavaFX JARs estão no classpath via manifesto (Java 17+)");
        System.out.println("Total de JARs em libs: " + jarFiles.length);

        // Verificar novamente se JavaFX está disponível
        if (!isJavaFXAvailable()) {
            throw new RuntimeException("JavaFX ainda não está disponível após configuração do classpath");
        }
    }

    /**
     * Extrai os natives (.dll) dos JARs do JavaFX e adiciona o diretório ao
     * java.library.path
     * Isso é CRUCIAL para que o WebView encontre jfxwebkit.dll
     */
    private static void extractAndLoadJavaFXNatives(File libsDir) {
        try {
            // Criar diretório temporário para natives
            File nativesDir = new File(System.getProperty("java.io.tmpdir"),
                    "javafx-natives-" + System.currentTimeMillis());
            nativesDir.mkdirs();
            System.out.println("Diretório de natives: " + nativesDir.getAbsolutePath());

            // Procurar por arquivos .dll nos JARs do JavaFX
            File[] javafxJars = libsDir.listFiles((dir, name) -> name.contains("javafx") && name.endsWith("-win.jar"));

            if (javafxJars == null || javafxJars.length == 0) {
                System.out.println("Aviso: Nenhum JAR do JavaFX com classificador -win encontrado");
                return;
            }

            System.out.println("Processando " + javafxJars.length + " JARs do JavaFX...");

            for (File jarFile : javafxJars) {
                System.out.println("Extraindo natives de: " + jarFile.getName());
                try (JarFile jar = new JarFile(jarFile)) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().endsWith(".dll") || entry.getName().endsWith(".so") ||
                                entry.getName().endsWith(".dylib")) {
                            String fileName = new File(entry.getName()).getName();
                            File nativeFile = new File(nativesDir, fileName);

                            // Extrair arquivo
                            try (InputStream is = jar.getInputStream(entry)) {
                                Files.copy(is, nativeFile.toPath(),
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                System.out.println("  Extraído: " + fileName);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Aviso ao processar " + jarFile.getName() + ": " + e.getMessage());
                }
            }

            // Adicionar diretório de natives ao java.library.path
            String currentLibPath = System.getProperty("java.library.path", "");
            String newLibPath = nativesDir.getAbsolutePath();
            if (!currentLibPath.isEmpty()) {
                newLibPath = newLibPath + File.pathSeparator + currentLibPath;
            }
            System.setProperty("java.library.path", newLibPath);
            System.out.println("java.library.path atualizado com: " + nativesDir.getAbsolutePath());

            // IMPORTANTE: Recarregar a lista de caminhos de biblioteca do ClassLoader
            // Isso faz o JVM procurar nos novos caminhos
            try {
                Field field = ClassLoader.class.getDeclaredField("sys_paths");
                field.setAccessible(true);
                field.set(null, null);
                System.out.println("sys_paths limpo - java.library.path recarregado");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                System.out.println("Aviso: Não foi possível recarregar sys_paths (esperado em Java 17+): "
                        + e.getClass().getSimpleName());
                // Em Java 17+, isso pode não funcionar, mas os natives foram extraídos e o path
                // foi definido
                // O ClassLoader nativo vai procurar no novo path de qualquer forma
            } catch (Exception e) {
                System.out.println("Aviso: Erro inesperado ao recarregar sys_paths: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Erro ao extrair natives do JavaFX: " + e.getMessage());
            e.printStackTrace();
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
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
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

        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InterruptedException
                | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            throw new RuntimeException("Falha ao inicializar JavaFX Platform", e);
        }
    }

    private static void handleLaunchError(Exception e) {
        System.err.println("=== ERRO CRÍTICO ===");
        System.err.println("Falha ao inicializar NetNotify Agent");
        System.err.println("Erro: " + e.getMessage());

        // Tentar mostrar dialog de erro
        try {
            showErrorDialog("Erro ao Inicializar NetNotify Agent",
                    """
                            N\u00e3o foi poss\u00edvel inicializar a aplica\u00e7\u00e3o.
                            Poss\u00edveis causas:
                            \u2022 Java 11+ n\u00e3o est\u00e1 instalado
                            \u2022 Depend\u00eancias JavaFX n\u00e3o encontradas na pasta 'libs'
                            \u2022 Problema de permiss\u00f5es

                            Detalhes t\u00e9cnicos:
                            """ + e.getMessage());
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
        } catch (HeadlessException e) {
            // Se não conseguir mostrar dialog, pelo menos imprimir
            System.err.println("ERRO: " + title);
            System.err.println(message);
        }
    }
}
