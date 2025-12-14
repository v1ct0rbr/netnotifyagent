package br.gov.pb.der.netnotifyagent.utils;

public class MessageUtils {
    public static final String TYPE_INFO = "Notícia";
    public static final String TYPE_WARNING = "Notificação";
    public static final String TYPE_ERROR = "Alerta";

    public static final String LEVEL_LOW = "Baixo";
    public static final String LEVEL_MEDIUM = "Normal";
    public static final String LEVEL_HIGH = "Alto";
    public static final String LEVEL_URGENTE = "Urgente";

    public static String formatMessageType(String type) {
        return switch (type) {
            case TYPE_INFO -> "[Notícia]";
            case TYPE_WARNING -> "[Notificação]";
            case TYPE_ERROR -> "[Alerta]";
            default -> "[Mensagem]";
        };
    }

    /*
     * <h1 style="color:blue;">[title]</h1> em caso de LEVEL_LOW
     * <h1 style="color:orange;">[title]</h1> em caso de LEVEL_MEDIUM
     * <h1 style="color:red;">[title]</h1> em caso de LEVEL_HIGH
     * <h1 style="color:darkred;">[title]</h1> em caso de LEVEL_URGENTE
     */

    public static String formatMessageLevel(String level, String title) {
        return switch (level) {
            case LEVEL_LOW -> "<h1 style=\"color:blue;\">" + title + "</h1>";
            case LEVEL_MEDIUM -> "<h1 style=\"color:orange;\">" + title + "</h1>";
            case LEVEL_HIGH -> "<h1 style=\"color:red;\">" + title + "</h1>";
            case LEVEL_URGENTE -> "<h1 style=\"color:darkred;\">" + title + "</h1>";

            // text cetralizado e negrito
            default -> "<h1 style=\"text-align:center;\">" + title + "</h1>";
        };
    }

}
