package br.gov.pb.der.netnotifyagent.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import br.gov.pb.der.netnotifyagent.dto.Message;

public class Functions {

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
        } catch (Exception e) {
            e.printStackTrace();
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