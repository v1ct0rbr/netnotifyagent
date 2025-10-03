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

    /*  const unescapeServerHtml = (raw: unknown): string => {
        if (typeof raw !== 'string') return '';
        let s = raw;
        // common escaped sequences from JSON-encoded HTML
        s = s.replace(/\\"/g, '"')
             .replace(/\\'/g, "'")
             .replace(/\\n/g, '\n')
             .replace(/\\r/g, '\r')
             .replace(/\\t/g, '\t')
             .replace(/\\\//g, '/')
             .replace(/\\\\/g, '\\')
             .replace(/\u2028/g, '\\u2028')
             .replace(/\u2029/g, '\\u2029');
        return s;
    }; */
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
}
