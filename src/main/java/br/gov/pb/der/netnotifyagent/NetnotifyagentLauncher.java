package br.gov.pb.der.netnotifyagent;

import java.awt.HeadlessException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import br.gov.pb.der.netnotifyagent.utils.AutoSetup;
import br.gov.pb.der.netnotifyagent.utils.Constants;

/**
 * Launcher inteligente que configura JavaFX automaticamente
 * para permitir execução via duplo clique e linha de comando.
 *
 * Se o JVM foi iniciado sem --module-path (ex: IDE, duplo-clique no JAR),
 * o JDK pode carregar o JavaFX 17 nativo em vez do JavaFX 22 das libs/.
 * Nesse caso, o launcher relança o processo com o --module-path correto.
 */
public class NetnotifyagentLauncher {

    private static final String JAVAFX_MODULES =
            "javafx.controls,javafx.web,javafx.swing,javafx.fxml,javafx.media";

    public static void main(String[] args) {
        System.out.println("=== NetNotify Agent Launcher ===");

        try {
            // Se o JDK carregou o JavaFX errado (ex: JavaFX 17 bundled), relançar
            // com o --module-path apontando para libs/ que contém o JavaFX 22.
            if (!isCorrectJavaFXVersion()) {
                File libsDir = findLibsDirectory();
                if (libsDir != null) {
                    System.out.println("JavaFX versão incorreta detectada. Relançando com --module-path...");
                    relaunchWithModulePath(libsDir, args);
                    return; // O processo relançado continuará a execução
                }
                System.err.println("AVISO: Versão incorreta do JavaFX e diretório libs/ não encontrado.");
            }

            validateJavaConfiguration();
            AutoSetup.ensureScheduledTaskAtLogon();
            detectExecutionContext();

            System.out.println("Iniciando aplicação principal...");
            Netnotifyagent.main(args);

        } catch (Exception e) {
            handleLaunchError(e);
        }
    }

    /**
     * Verifica se a versão do JavaFX carregada é 22 ou superior.
     * Retorna true se a versão estiver correta ou não puder ser determinada.
     */
    private static boolean isCorrectJavaFXVersion() {
        try {
            Class<?> platformClass = Class.forName("javafx.application.Platform");
            Module m = platformClass.getModule();
            if (!m.isNamed()) return true; // Carregado do classpath, não do sistema de módulos
            var versionOpt = m.getDescriptor().version();
            if (versionOpt.isEmpty()) return true;
            int major = Integer.parseInt(versionOpt.get().toString().split("[.+\\-]")[0]);
            return major >= 22;
        } catch (ClassNotFoundException e) {
            return true; // JavaFX ausente; falhará depois com mensagem clara
        } catch (Exception e) {
            return true; // Não foi possível determinar; prosseguir
        }
    }

    /**
     * Relança o processo atual com --module-path apontando para libsDir,
     * garantindo que o JavaFX 22 seja carregado em vez do JavaFX 17 do JDK.
     */
    private static void relaunchWithModulePath(File libsDir, String[] originalArgs) {
        String javaHome = System.getProperty("java.home");
        File java  = new File(javaHome, "bin" + File.separator + "java.exe");
        File javaw = new File(javaHome, "bin" + File.separator + "javaw.exe");
        File javaUnix = new File(javaHome, "bin" + File.separator + "java");
        String javaExe = java.exists() ? java.getAbsolutePath()
                : javaw.exists() ? javaw.getAbsolutePath()
                : javaUnix.exists() ? javaUnix.getAbsolutePath()
                : "java";

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("--module-path");
        cmd.add(libsDir.getAbsolutePath());
        cmd.add("--add-modules");
        cmd.add(JAVAFX_MODULES);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path", "."));
        cmd.add(NetnotifyagentLauncher.class.getName());
        cmd.addAll(Arrays.asList(originalArgs));

        System.out.println("Relançamento: " + javaExe + " --module-path " + libsDir.getName() + " ...");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(System.getProperty("user.dir")));
        pb.inheritIO();
        try {
            System.exit(pb.start().waitFor());
        } catch (Exception e) {
            System.err.println("Falha no relançamento: " + e.getMessage());
            handleLaunchError(new RuntimeException("Falha no relançamento com --module-path", e));
        }
    }

    private static void validateJavaConfiguration() {
        try {
            Properties settings = loadSettings();
            String configuredJavaHome = settings.getProperty("java.home", "").trim();

            if (!configuredJavaHome.isEmpty()) {
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
                    System.err.println("AVISO: Executável Java não encontrado em: " + configuredJavaHome + File.separator + "bin");
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

        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java home: " + System.getProperty("java.home"));
        System.out.println("Working dir: " + System.getProperty("user.dir"));
    }

    private static File findLibsDirectory() {
        List<String> searchPaths = new ArrayList<>();

        try {
            String jarPath = NetnotifyagentLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            File jarFile = new File(jarPath);
            File jarDir = jarFile.getParentFile();
            searchPaths.add(new File(jarDir, "libs").getPath());
        } catch (Exception e) {
            System.out.println("Não foi possível determinar localização do JAR: " + e.getMessage());
        }

        searchPaths.add("libs");
        searchPaths.add("./libs");
        searchPaths.add("target/libs");
        searchPaths.add("target/dependency");

        for (String path : searchPaths) {
            File dir = new File(path);
            System.out.println("Procurando libs em: " + dir.getAbsolutePath());
            if (dir.exists() && dir.isDirectory()) {
                File[] javaFxJars = dir.listFiles(
                        (d, name) -> name.toLowerCase().contains("javafx") && name.endsWith(".jar"));
                if (javaFxJars != null && javaFxJars.length > 0) {
                    System.out.println("JavaFX JARs encontrados: " + javaFxJars.length);
                    return dir;
                }
            }
        }

        return null;
    }

    /**
     * Extrai os natives (.dll) dos JARs do JavaFX e adiciona o diretório ao
     * java.library.path. Usado apenas como fallback quando o --module-path
     * não está disponível.
     */
    @SuppressWarnings("unused")
    private static void extractAndLoadJavaFXNatives(File libsDir) {
        try {
            File nativesDir = new File(System.getProperty("java.io.tmpdir"),
                    "javafx-natives-" + System.currentTimeMillis());
            nativesDir.mkdirs();
            System.out.println("Diretório de natives: " + nativesDir.getAbsolutePath());

            File[] javafxJars = libsDir.listFiles(
                    (dir, name) -> name.contains("javafx") && name.endsWith("-win.jar"));

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

            String currentLibPath = System.getProperty("java.library.path", "");
            String newLibPath = nativesDir.getAbsolutePath();
            if (!currentLibPath.isEmpty()) {
                newLibPath = newLibPath + File.pathSeparator + currentLibPath;
            }
            System.setProperty("java.library.path", newLibPath);

            try {
                Field field = ClassLoader.class.getDeclaredField("sys_paths");
                field.setAccessible(true);
                field.set(null, null);
            } catch (Exception e) {
                System.out.println("Aviso: Não foi possível recarregar sys_paths: " + e.getClass().getSimpleName());
            }

        } catch (Exception e) {
            System.out.println("Erro ao extrair natives do JavaFX: " + e.getMessage());
        }
    }

    private static void handleLaunchError(Exception e) {
        System.err.println("=== ERRO CRÍTICO ===");
        System.err.println("Falha ao inicializar NetNotify Agent");
        System.err.println("Erro: " + e.getMessage());

        try {
            showErrorDialog("Erro ao Inicializar NetNotify Agent",
                    """
                            Não foi possível inicializar a aplicação.
                            Possíveis causas:
                            • Java 17+ não está instalado
                            • Dependências JavaFX não encontradas na pasta 'libs'
                            • Problema de permissões

                            Detalhes técnicos:
                            """ + e.getMessage());
        } catch (Exception dialogError) {
            System.err.println("Não foi possível mostrar dialog de erro: " + dialogError.getMessage());
        }

        System.exit(1);
    }

    private static void showErrorDialog(String title, String message) {
        try {
            javax.swing.JOptionPane.showMessageDialog(null, message, title,
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        } catch (HeadlessException e) {
            System.err.println("ERRO: " + title);
            System.err.println(message);
        }
    }
}
