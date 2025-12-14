package br.gov.pb.der.netnotifyagent.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.gov.pb.der.netnotifyagent.dto.Message;

public class Functions {

    public static String addHtmlTagsToContent(Message message) {
        if (message == null || message.getContent() == null || message.getContent().isEmpty()) {
            return message != null && message.getContent() != null ? message.getContent() : "";
        }
        // Inline remote images before wrapping in HTML tags
        String processedContent = inlineRemoteImages(message.getContent());
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"UTF-8\">");
        sb.append(
                "<link href=\"https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>");
        sb.append("body { font-family: Arial, sans-serif; margin: 10px; }");
        sb.append("img { max-width: 100%; height: auto; }");
        sb.append("</style>");
        sb.append("</head><body>");
        sb.append(MessageUtils.formatMessageLevel(message.getLevel(), message.getTitle()));
        sb.append(processedContent);
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String inlineRemoteImages(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Pattern p = Pattern.compile("<img\\s+[^>]*src\\s*=\\s*\"(https?://[^\"\\s>]+)\"[^>]*>",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String urlStr = m.group(1);
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "NetNotifyAgent/1.0");
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                    }
                    byte[] bytes = out.toByteArray();
                    String contentType = conn.getContentType();
                    if (contentType == null) {
                        contentType = "image/png";
                    }
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    String dataUri = "data:" + contentType + ";base64," + base64;
                    String originalTag = m.group(0);
                    String replacement = originalTag.replace(urlStr, dataUri);
                    m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
            } catch (IOException ex) {
                // não conseguiu baixar, deixa a tag original
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Properties loadProperties(String propertiesPath) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get(propertiesPath))) {
            properties.load(input);
        }
        return properties;
    }

    public static Message jsonToObject(String json, Class<Message> aClass) {
        Message message = null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            message = objectMapper.readValue(json, aClass);
        } catch (IOException e) {
            System.err.println("Erro ao processar JSON: " + e.getMessage());
        }
        return message;
    }

    /*
     * const unescapeServerHtml = (raw: unknown): string => {
     * if (typeof raw !== 'string') return '';
     * let s = raw;
     * // common escaped sequences from JSON-encoded HTML
     * s = s.replace(/\\"/g, '"')
     * .replace(/\\'/g, "'")
     * .replace(/\\n/g, '\n')
     * .replace(/\\r/g, '\r')
     * .replace(/\\t/g, '\t')
     * .replace(/\\\//g, '/')
     * .replace(/\\\\/g, '\\')
     * .replace(/\u2028/g, '\\u2028')
     * .replace(/\u2029/g, '\\u2029');
     * return s;
     * };
     */
    public static String unescapeServerHtml(String encodedHtmlText) {
        if (encodedHtmlText == null || encodedHtmlText.isEmpty()) {
            return "";
        }
        String s = encodedHtmlText;
        // common escaped sequences from JSON-encoded HTML
        s = s.replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\/", "/")
                .replace("\\\\", "\\")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
        return s;
    }

    public static String lineBuilder(String text, char ch, int totalLength) {
        StringBuilder sb = new StringBuilder();

        // Espaço disponível para texto (totalLength - 4 para "║ " e " ║")
        int maxTextLength = totalLength - 4;

        if (text.length() <= maxTextLength) {
            // Texto cabe em uma linha
            sb.append("║ ").append(text);

            // Preenchimento com caractere
            int remainingLength = totalLength - text.length() - 4;
            for (int i = 0; i < remainingLength; i++) {
                sb.append(ch);
            }
            sb.append(" ║");
        } else {
            // Texto maior que o espaço: quebra em múltiplas linhas
            int index = 0;
            while (index < text.length()) {
                int endIndex = Math.min(index + maxTextLength, text.length());
                String line = text.substring(index, endIndex);

                sb.append("║ ").append(line);

                // Preenchimento com caractere na última linha
                if (endIndex >= text.length()) {
                    int remainingLength = maxTextLength - line.length();
                    for (int i = 0; i < remainingLength; i++) {
                        sb.append(ch);
                    }
                } else {
                    // Linha intermediária: preenchimento com espaços
                    int remainingLength = maxTextLength - line.length();
                    for (int i = 0; i < remainingLength; i++) {
                        sb.append(" ");
                    }
                }
                sb.append(" ║\n");
                index = endIndex;
            }
            // Remove a última quebra de linha
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.deleteCharAt(sb.length() - 1);
            }
        }

        return sb.toString();
    }

    public static String divideLine(char ch, int totalLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("╠");
        for (int i = 0; i < totalLength - 2; i++) { // 2 accounts for "╠" and "╣"
            sb.append(ch);
        }
        sb.append("╣");
        return sb.toString();
    }

    public static String headerLine(char ch, int totalLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔");
        for (int i = 0; i < totalLength - 2; i++) { // 2 accounts for "╔" and "╗"
            sb.append(ch);
        }
        sb.append("╗");
        return sb.toString();
    }

    public static String footerLine(char ch, int totalLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("╚");
        for (int i = 0; i < totalLength - 2; i++) { // 2 accounts for "╚" and "╝"
            sb.append(ch);
        }
        sb.append("╝");
        return sb.toString();
    }
}