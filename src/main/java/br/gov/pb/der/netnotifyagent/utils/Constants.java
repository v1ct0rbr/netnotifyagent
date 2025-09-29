package br.gov.pb.der.netnotifyagent.utils;

public class Constants {

    public static final String TRAY_ICON_TOOLTIP = "NetNotify Agent";
    public static final String STATUS_WINDOW_TITLE = "NetNotify — Status";
    public static final String STATUS_WINDOW_PREF_WIDTH = "600";
    public static final String STATUS_WINDOW_PREF_HEIGHT = "300";
    public static final String STATUS_WINDOW_TEXTAREA_STYLE = "-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 12px;";
    public static final String ALERT_TITLE = "NetNotify Agent";
    public static final String ALERT_HEADER = "Mensagem Recebida";
    public static final String ALERT_DEFAULT_CONTENT = "(Sem conteúdo)";
    public static final int RABBITMQ_RECONNECT_DELAY_MS = 5000;
    public static final int RABBITMQ_HEARTBEAT_INTERVAL_MS = 60000;

    public static final String SETTINGS_FILE_PATH = "resources/settings.properties";
    public static final String LOG_FILE_PATH = "resources/netnotifyagent.log";
    public static final String ICON_48PX_PATH = "/images/icon_48px.png";

    public static final String APP_CREATOR = "Victor Queiroga";
    public static final String APP_VERSION = "1.0.0";
    public static final String APP_COPYRIGHT = "© 2025 DER-PB. Todos os direitos reservados.";

    private Constants() {
        // Construtor privado para evitar instanciação
    }

    public static String getAppInfo() {
        return String.format("%s\nVersão: %s\n%s", APP_CREATOR, APP_VERSION, APP_COPYRIGHT);
    }

}
