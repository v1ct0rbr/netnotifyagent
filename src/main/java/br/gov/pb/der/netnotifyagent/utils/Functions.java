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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Functions {

    private static final Logger logger = LoggerFactory.getLogger(Functions.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[a-zA-Z][^>]*>", Pattern.DOTALL);

    public static String addHtmlTagsToContent(Message message) {
        if (message == null || message.content() == null || message.content().isEmpty()) {
            return message != null && message.content() != null ? message.content() : "";
        }
        String safeTitle = stripEmoji(message.title() != null ? message.title() : "");
        String rawContent = message.content();
        String processedContent;
        if (HTML_TAG_PATTERN.matcher(rawContent).find()) {
            // HTML content: strip emoji from text nodes only, then inline remote images
            processedContent = inlineRemoteImages(stripEmojiFromHtml(rawContent));
        } else {
            // Plain text: strip emoji and preserve line breaks
            processedContent = "<p style=\"white-space:pre-wrap;word-wrap:break-word\">"
                    + stripEmoji(rawContent).trim() + "</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"UTF-8\">");
        sb.append("<link href=\"https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap\" rel=\"stylesheet\">");
        sb.append("<style>body { font-family: Arial, sans-serif; margin: 10px; } img { max-width: 100%; height: auto; }</style>");
        sb.append("</head><body>");
        sb.append(MessageUtils.formatMessageLevel(message.level(), safeTitle));
        sb.append(processedContent);
        sb.append("</body></html>");
        return sb.toString();
    }

    /** Removes all emoji code points from plain text. */
    public static String stripEmoji(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (!isEmojiCodePoint(cp)) {
                result.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return result.toString();
    }

    /** Removes emoji code points from text nodes in an HTML string, leaving tags and entities untouched. */
    public static String stripEmojiFromHtml(String html) {
        if (html == null || html.isEmpty()) return html;
        java.util.regex.Pattern tagOrEntity = java.util.regex.Pattern.compile(
                "<[^>]*>|&(?:#\\d+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = tagOrEntity.matcher(html);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            sb.append(stripEmoji(html.substring(lastEnd, m.start())));
            sb.append(m.group());
            lastEnd = m.end();
        }
        sb.append(stripEmoji(html.substring(lastEnd)));
        return sb.toString();
    }

    private static boolean isEmojiCodePoint(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FFFF)   // Supplementary Multilingual Plane
            || (cp >= 0x2600  && cp <= 0x26FF)     // Miscellaneous Symbols
            || (cp >= 0x2700  && cp <= 0x27FF)     // Dingbats
            || (cp >= 0x1FA00 && cp <= 0x1FAFF)    // Symbols and Pictographs Extended-A
            || (cp >= 0xFE00  && cp <= 0xFE0F)     // Variation Selectors (leftover after base emoji stripped)
            || cp == 0x200D                         // Zero Width Joiner (ZWJ sequences)
            || cp == 0x20E3                         // Combining Enclosing Keycap
            || cp == 0x00A9 || cp == 0x00AE;       // ©️ ®️
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
            message = MAPPER.readValue(json, aClass);
        } catch (IOException e) {
            logger.error("Erro ao processar JSON: {}", e.getMessage());
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