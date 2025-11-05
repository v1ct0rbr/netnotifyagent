package br.gov.pb.der.netnotifyagent.utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Utility to summarize current Java runtime and any overrides configured
 * in settings.properties (java.executable/java.home).
 */
public final class JavaRuntimeInfo {

    private JavaRuntimeInfo() { }

    public static String getSummary() {
        StringBuilder sb = new StringBuilder();

        String javaVersion = System.getProperty("java.version", "unknown");
        String javaVendor = System.getProperty("java.vendor", "unknown");
        String runtimeName = System.getProperty("java.runtime.name", "");
        String runtimeVersion = System.getProperty("java.runtime.version", "");
        String vmName = System.getProperty("java.vm.name", "");
        String vmVersion = System.getProperty("java.vm.version", "");
        String javaHome = System.getProperty("java.home", "");

        sb.append("Versão: ").append(javaVersion).append('\n');
        if (!runtimeName.isEmpty()) {
            sb.append("Runtime: ").append(runtimeName);
            if (!runtimeVersion.isEmpty()) sb.append(" ").append(runtimeVersion);
            sb.append('\n');
        }
        if (!vmName.isEmpty()) {
            sb.append("VM: ").append(vmName);
            if (!vmVersion.isEmpty()) sb.append(" ").append(vmVersion);
            sb.append('\n');
        }
        if (!javaHome.isEmpty()) {
            sb.append("java.home: ").append(javaHome).append('\n');
        }

        // Guess effective Java executable from java.home
        String guessedExe = guessJavaExecutable(javaHome);
        if (guessedExe != null) {
            sb.append("Java em uso (estimado): ").append(guessedExe).append('\n');
        }

        // Read overrides from settings.properties if available
        String settingsPath = Constants.SETTINGS_FILE_PATH;
        File f = new File(settingsPath);
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                Properties p = new Properties();
                p.load(fis);
                String cfgExe = trimQuotes(p.getProperty("java.executable", ""));
                String cfgHome = trimQuotes(p.getProperty("java.home", ""));
                if (!cfgExe.isEmpty()) {
                    sb.append("Configurado (settings) java.executable: ").append(cfgExe).append('\n');
                }
                if (!cfgHome.isEmpty()) {
                    sb.append("Configurado (settings) java.home: ").append(cfgHome).append('\n');
                }
            } catch (Exception ignored) { }
        }

        return sb.toString().trim();
    }

    private static String trimQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String guessJavaExecutable(String javaHome) {
        if (javaHome == null || javaHome.isEmpty()) return null;
        String[] candidates = new String[] {
                javaHome + File.separator + "bin" + File.separator + "javaw.exe",
                javaHome + File.separator + "bin" + File.separator + "java.exe",
                javaHome + File.separator + "bin" + File.separator + "java"
        };
        for (String c : candidates) {
            if (new File(c).exists()) return c;
        }
        return null;
    }
}
