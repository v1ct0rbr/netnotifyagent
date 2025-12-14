package br.gov.pb.der.netnotifyagent.ui;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.gov.pb.der.netnotifyagent.dto.Message;
import br.gov.pb.der.netnotifyagent.utils.Functions;

public class ShowInBrowser {

    private static volatile ShowInBrowser INSTANCE;
    private final ObjectMapper objectMapper;

    public ShowInBrowser() {
        this.objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    public static ShowInBrowser getInstance() {
        if (INSTANCE == null) {
            synchronized (ShowInBrowser.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ShowInBrowser();
                }
            }
        }
        return INSTANCE;
    }

    public void showInfo(String message) {
        try {
            Message msgObj = objectMapper.readValue(message, Message.class);
            // cria uma arquivo html temporario
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("netnotify_message_", ".html");

            String htmlContent = Functions.addHtmlTagsToContent(msgObj);

            java.nio.file.Files.writeString(tempFile, htmlContent);
            // abre o arquivo no navegador padrao
            java.awt.Desktop.getDesktop().browse(tempFile.toUri());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
