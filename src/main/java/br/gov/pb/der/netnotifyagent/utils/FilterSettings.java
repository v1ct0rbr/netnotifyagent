package br.gov.pb.der.netnotifyagent.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Gerencia as configurações de filtro de mensagens por nível
 * Salva e carrega as preferências do usuário em settings.properties
 */
public class FilterSettings {

    private static final String FILTER_BAIXO_KEY = "filter.level.baixo.enabled";
    private static final String FILTER_NORMAL_KEY = "filter.level.normal.enabled";
    private static final String FILTER_ALTO_KEY = "filter.level.alto.enabled";
    private static final String FILTER_URGENTE_KEY = "filter.level.urgente.enabled";

    // Valores padrão: mostrar todas as mensagens
    private static final boolean DEFAULT_BAIXO = true;
    private static final boolean DEFAULT_NORMAL = true;
    private static final boolean DEFAULT_ALTO = true;
    private static final boolean DEFAULT_URGENTE = true;

    private static final String SETTINGS_FILE = "resources/settings.properties";
    private static Properties properties = new Properties();

    static {
        loadSettings();
    }

    /**
     * Carrega as configurações de settings.properties
     */
    private static void loadSettings() {
        try {
            File settingsFile = new File(SETTINGS_FILE);
            if (settingsFile.exists()) {
                try (FileInputStream fis = new FileInputStream(settingsFile)) {
                    properties.load(fis);
                }
                System.out.println("[FilterSettings] Configurações carregadas de: " + SETTINGS_FILE);
            } else {
                System.out.println("[FilterSettings] Arquivo de configurações não encontrado, usando padrões");
                // Criar propriedades padrão
                setDefaults();
            }
        } catch (IOException e) {
            System.out.println("[FilterSettings] Erro ao carregar configurações: " + e.getMessage());
            setDefaults();
        }
    }

    /**
     * Define os valores padrão
     */
    private static void setDefaults() {
        properties.setProperty(FILTER_BAIXO_KEY, String.valueOf(DEFAULT_BAIXO));
        properties.setProperty(FILTER_NORMAL_KEY, String.valueOf(DEFAULT_NORMAL));
        properties.setProperty(FILTER_ALTO_KEY, String.valueOf(DEFAULT_ALTO));
        properties.setProperty(FILTER_URGENTE_KEY, String.valueOf(DEFAULT_URGENTE));
    }

    /**
     * Salva as configurações em settings.properties
     */
    public static void saveSettings() {
        try {
            File settingsFile = new File(SETTINGS_FILE);
            settingsFile.getParentFile().mkdirs(); // Criar diretório se não existir
            
            try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
                properties.store(fos, "NetNotify Agent - Configurações de Filtro de Mensagens");
                System.out.println("[FilterSettings] Configurações salvas com sucesso");
            }
        } catch (IOException e) {
            System.out.println("[FilterSettings] Erro ao salvar configurações: " + e.getMessage());
        }
    }

    /**
     * Define se deve mostrar mensagens com nível "Baixo"
     */
    public static void setBaixoEnabled(boolean enabled) {
        properties.setProperty(FILTER_BAIXO_KEY, String.valueOf(enabled));
    }

    /**
     * Define se deve mostrar mensagens com nível "Normal"
     */
    public static void setNormalEnabled(boolean enabled) {
        properties.setProperty(FILTER_NORMAL_KEY, String.valueOf(enabled));
    }

    /**
     * Define se deve mostrar mensagens com nível "Alto"
     */
    public static void setAltoEnabled(boolean enabled) {
        properties.setProperty(FILTER_ALTO_KEY, String.valueOf(enabled));
    }

    /**
     * Define se deve mostrar mensagens com nível "Urgente"
     */
    public static void setUrgenteEnabled(boolean enabled) {
        properties.setProperty(FILTER_URGENTE_KEY, String.valueOf(enabled));
    }

    /**
     * Verifica se deve mostrar mensagens com nível "Baixo"
     */
    public static boolean isBaixoEnabled() {
        return Boolean.parseBoolean(properties.getProperty(FILTER_BAIXO_KEY, String.valueOf(DEFAULT_BAIXO)));
    }

    /**
     * Verifica se deve mostrar mensagens com nível "Normal"
     */
    public static boolean isNormalEnabled() {
        return Boolean.parseBoolean(properties.getProperty(FILTER_NORMAL_KEY, String.valueOf(DEFAULT_NORMAL)));
    }

    /**
     * Verifica se deve mostrar mensagens com nível "Alto"
     */
    public static boolean isAltoEnabled() {
        return Boolean.parseBoolean(properties.getProperty(FILTER_ALTO_KEY, String.valueOf(DEFAULT_ALTO)));
    }

    /**
     * Verifica se deve mostrar mensagens com nível "Urgente"
     */
    public static boolean isUrgenteEnabled() {
        return Boolean.parseBoolean(properties.getProperty(FILTER_URGENTE_KEY, String.valueOf(DEFAULT_URGENTE)));
    }

    /**
     * Verifica se uma mensagem deve ser exibida com base em seu nível
     * @param level Nível da mensagem (e.g., "Baixo", "Normal", "Alto", "Urgente")
     * @return true se a mensagem deve ser exibida, false caso contrário
     */
    public static boolean shouldShowMessage(String level) {
        if (level == null) {
            return true; // Mostrar mensagens sem nível definido
        }

        String normalizedLevel = level.trim().toLowerCase();

        switch (normalizedLevel) {
            case "baixo":
                return isBaixoEnabled();
            case "normal":
                return isNormalEnabled();
            case "alto":
                return isAltoEnabled();
            case "urgente":
                return true;
            default:
                return true; // Mostrar níveis desconhecidos
        }
    }

    /**
     * Retorna um resumo das configurações atuais
     */
    public static String getSummary() {
        return "Filtros de Mensagens:\n" +
                "  Baixo: " + (isBaixoEnabled() ? "Ativado" : "Desativado") + "\n" +
                "  Normal: " + (isNormalEnabled() ? "Ativado" : "Desativado") + "\n" +
                "  Alto: " + (isAltoEnabled() ? "Ativado" : "Desativado") + "\n" +
                "  Urgente: " + (isUrgenteEnabled() ? "Ativado" : "Desativado");
    }

    /**
     * Recarrega as configurações do arquivo
     */
    public static void reload() {
        loadSettings();
    }
}
