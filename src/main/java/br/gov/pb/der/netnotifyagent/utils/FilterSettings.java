package br.gov.pb.der.netnotifyagent.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gerencia as configuracoes de filtro de mensagens por nivel
 * Salva e carrega as preferencias do usuario em settings.properties
 */
public class FilterSettings {

    private static final Logger logger = LoggerFactory.getLogger(FilterSettings.class);

    private static final String FILTER_BAIXO_KEY = "filter.level.baixo.enabled";
    private static final String FILTER_NORMAL_KEY = "filter.level.normal.enabled";
    private static final String FILTER_ALTO_KEY = "filter.level.alto.enabled";
    private static final String FILTER_URGENTE_KEY = "filter.level.urgente.enabled";

    private static final boolean DEFAULT_BAIXO = true;
    private static final boolean DEFAULT_NORMAL = true;
    private static final boolean DEFAULT_ALTO = true;
    private static final boolean DEFAULT_URGENTE = true;

    private static final String SETTINGS_FILE = "resources/settings.properties";
    private static Properties properties = new Properties();

    static {
        loadSettings();
    }

    private static synchronized void loadSettings() {
        try {
            File settingsFile = new File(SETTINGS_FILE);
            if (settingsFile.exists()) {
                try (FileInputStream fis = new FileInputStream(settingsFile)) {
                    properties.load(fis);
                }
                logger.info("[FilterSettings] Configuracoes carregadas de: {}", SETTINGS_FILE);
                String urgenteProp = properties.getProperty(FILTER_URGENTE_KEY);
                if (!"true".equalsIgnoreCase(urgenteProp)) {
                    properties.setProperty(FILTER_URGENTE_KEY, "true");
                    saveSettings();
                }
            } else {
                logger.info("[FilterSettings] Arquivo de configuracoes nao encontrado, usando padroes");
                setDefaults();
            }
        } catch (IOException e) {
            logger.warn("[FilterSettings] Erro ao carregar configuracoes: {}", e.getMessage());
            setDefaults();
        }
    }

    private static void setDefaults() {
        properties.setProperty(FILTER_BAIXO_KEY, String.valueOf(DEFAULT_BAIXO));
        properties.setProperty(FILTER_NORMAL_KEY, String.valueOf(DEFAULT_NORMAL));
        properties.setProperty(FILTER_ALTO_KEY, String.valueOf(DEFAULT_ALTO));
        properties.setProperty(FILTER_URGENTE_KEY, String.valueOf(DEFAULT_URGENTE));
    }

    public static synchronized void saveSettings() {
        try {
            properties.setProperty(FILTER_URGENTE_KEY, "true");
            Map<String, String> updates = new LinkedHashMap<>();
            updates.put(FILTER_BAIXO_KEY, properties.getProperty(FILTER_BAIXO_KEY, String.valueOf(DEFAULT_BAIXO)));
            updates.put(FILTER_NORMAL_KEY, properties.getProperty(FILTER_NORMAL_KEY, String.valueOf(DEFAULT_NORMAL)));
            updates.put(FILTER_ALTO_KEY, properties.getProperty(FILTER_ALTO_KEY, String.valueOf(DEFAULT_ALTO)));
            updates.put(FILTER_URGENTE_KEY, "true");
            PropertiesFileUtil.updateProperties(new File(SETTINGS_FILE), updates);
            logger.info("[FilterSettings] Configuracoes salvas com sucesso");
        } catch (IOException e) {
            logger.warn("[FilterSettings] Erro ao salvar configuracoes: {}", e.getMessage());
        }
    }

    public static synchronized void setBaixoEnabled(boolean enabled) {
        properties.setProperty(FILTER_BAIXO_KEY, String.valueOf(enabled));
    }

    public static synchronized void setNormalEnabled(boolean enabled) {
        properties.setProperty(FILTER_NORMAL_KEY, String.valueOf(enabled));
    }

    public static synchronized void setAltoEnabled(boolean enabled) {
        properties.setProperty(FILTER_ALTO_KEY, String.valueOf(enabled));
    }

    public static synchronized void setUrgenteEnabled(boolean enabled) {
        // Ignorar tentativa de desativar URGENTE - manter sempre true
        properties.setProperty(FILTER_URGENTE_KEY, "true");
    }

    public static synchronized boolean isBaixoEnabled() {
        return Boolean.parseBoolean(properties.getProperty(FILTER_BAIXO_KEY, String.valueOf(DEFAULT_BAIXO)));
    }

    public static synchronized boolean isNormalEnabled() {
        return Boolean.parseBoolean(properties.getProperty(FILTER_NORMAL_KEY, String.valueOf(DEFAULT_NORMAL)));
    }

    public static synchronized boolean isAltoEnabled() {
        return Boolean.parseBoolean(properties.getProperty(FILTER_ALTO_KEY, String.valueOf(DEFAULT_ALTO)));
    }

    public static synchronized boolean isUrgenteEnabled() {
        // URGENTE e sempre considerado ativo
        return true;
    }

    public static synchronized boolean shouldShowMessage(String level) {
        if (level == null) return true;
        return switch (level.trim().toLowerCase()) {
            case "baixo" -> isBaixoEnabled();
            case "normal" -> isNormalEnabled();
            case "alto" -> isAltoEnabled();
            default -> true;
        };
    }

    public static synchronized String getSummary() {
        return "Filtros de Mensagens:\n" +
                "  Baixo: " + (isBaixoEnabled() ? "Ativado" : "Desativado") + "\n" +
                "  Normal: " + (isNormalEnabled() ? "Ativado" : "Desativado") + "\n" +
                "  Alto: " + (isAltoEnabled() ? "Ativado" : "Desativado") + "\n" +
                "  Urgente: " + (isUrgenteEnabled() ? "Ativado" : "Desativado");
    }

    public static synchronized void reload() {
        loadSettings();
    }
}
