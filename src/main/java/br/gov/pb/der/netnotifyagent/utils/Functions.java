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

}
