package br.gov.pb.der.netnotifyagent.ui;

import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.gov.pb.der.netnotifyagent.dto.Message;
import br.gov.pb.der.netnotifyagent.utils.Functions;
import br.gov.pb.der.netnotifyagent.utils.MessageUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Alert {

    private static final Logger logger = LoggerFactory.getLogger(Alert.class);

    private static volatile Alert instance;
    private final ObjectMapper objectMapper;

    private Alert() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
    }

    public static Alert getInstance() {
        Alert inst = Alert.instance;
        if (inst == null) {
            synchronized (Alert.class) {
                inst = Alert.instance;
                if (inst == null) {
                    Alert.instance = inst = new Alert();
                }
            }
        }
        return inst;
    }

    public void showInfo(String message) {
        try {
            Message msg = objectMapper.readValue(message, Message.class);
            FxJavaInitializer.ensureInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.NONE);
                stage.setAlwaysOnTop(true);
                Image icon = FxJavaInitializer.loadFxIcon();
                if (icon != null)
                    stage.getIcons().add(icon);
                stage.setTitle("Alerta");
                Text txt = new Text(msg.content());
                txt.wrappingWidthProperty().set(600);
                ScrollPane sp = new ScrollPane(txt);
                sp.setPrefSize(600, 200);
                VBox root = new VBox(sp);
                root.setPadding(new Insets(10));
                stage.setScene(new Scene(root));
                stage.show();
                stage.toFront();
            });
        } catch (JsonProcessingException e) {
            FxJavaInitializer.ensureInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                Image icon = FxJavaInitializer.loadFxIcon();
                if (icon != null)
                    stage.getIcons().add(icon);
                stage.setTitle("Alerta");
                Text txt = new Text(message);
                txt.wrappingWidthProperty().set(600);
                ScrollPane sp = new ScrollPane(txt);
                sp.setPrefSize(600, 200);
                VBox root = new VBox(sp);
                root.setPadding(new Insets(10));
                stage.setScene(new Scene(root));
                stage.show();
                stage.toFront();
            });
        }
    }

    public void showError(String message) {
        FxJavaInitializer.ensureInitialized();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.NONE);
            stage.setAlwaysOnTop(true);
            Image icon = FxJavaInitializer.loadFxIcon();
            if (icon != null)
                stage.getIcons().add(icon);
            stage.setTitle("Erro");
            Text txt = new Text(message);
            txt.wrappingWidthProperty().set(600);
            ScrollPane sp = new ScrollPane(txt);
            sp.setPrefSize(600, 200);
            VBox root = new VBox(sp);
            root.setPadding(new Insets(10));
            stage.setScene(new Scene(root));
            stage.show();
            stage.toFront();
        });
    }

    public void showHtml(String htmlContent) {
        try {
            Message message = objectMapper.readValue(htmlContent, Message.class);
            logger.info("Parsed message: {}", message.content());

            FxJavaInitializer.ensureInitialized();

            final String contentToLoad = sanitizeHtml(Functions.addHtmlTagsToContent(message));
            final String fullHtmlForBrowser = Functions.addHtmlTagsToContent(message);

            Platform.runLater(() -> {
                try {
                    Stage stage = new Stage();
                    stage.initModality(Modality.NONE);
                    stage.setAlwaysOnTop(true);
                    stage.setResizable(true);

                    Image icon = FxJavaInitializer.loadFxIcon();
                    if (icon != null)
                        stage.getIcons().add(icon);
                    stage.setTitle(
                            message.type() != null ? MessageUtils.formatMessageType(message.type()) : "Mensagem");

                    WebView webView = new WebView();
                    WebEngine engine = webView.getEngine();

                    engine.setJavaScriptEnabled(false);
                    webView.setContextMenuEnabled(false);

                    double maxW = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth() * 0.9;
                    double maxH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() * 0.9;

                    engine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
                        if (newEx != null)
                            logger.error("WebEngine load error: {}", newEx.getMessage());
                    });

                    engine.loadContent(contentToLoad, "text/html");

                    engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        try {
                            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                                // habilita JS temporariamente apenas para medir dimensoes do documento
                                engine.setJavaScriptEnabled(true);
                                Object wObj = engine.executeScript(
                                        "Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)");
                                Object hObj = engine.executeScript(
                                        "Math.max(document.documentElement.scrollHeight, document.body.scrollHeight)");
                                engine.setJavaScriptEnabled(false);

                                double contentW = (wObj instanceof Number) ? ((Number) wObj).doubleValue() : 800;
                                double contentH = (hObj instanceof Number) ? ((Number) hObj).doubleValue() : 600;

                                double paddingW = 20;
                                double paddingH = 80;
                                double finalW = Math.min(contentW + paddingW, maxW);
                                double finalH = Math.min(contentH + paddingH, maxH);

                                webView.setPrefWidth(finalW);
                                webView.setPrefHeight(finalH);
                                stage.setWidth(finalW);
                                stage.setHeight(finalH);
                                stage.setMinWidth(300);
                                stage.setMinHeight(150);
                                stage.centerOnScreen();
                                stage.toFront();
                            }
                        } catch (Exception ex) {
                            logger.error("Erro ao ajustar tamanho dinamico: {}", ex.getMessage());
                        }
                    });

                    Button openInBrowserButton = new Button("Abrir no navegador");
                    openInBrowserButton.setOnAction(evt -> {
                        try {
                            if (!Desktop.isDesktopSupported()
                                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                logger.error("Desktop browse not supported");
                                return;
                            }
                            Path tempFile = Files.createTempFile("netnotifyagent-msg-", ".html");
                            tempFile.toFile().deleteOnExit(); // garante limpeza ao sair
                            Files.writeString(tempFile, fullHtmlForBrowser, StandardCharsets.UTF_8);
                            Desktop.getDesktop().browse(tempFile.toUri());
                            Thread.ofVirtual().name("temp-cleanup").start(() -> {
                                try {
                                    Thread.sleep(30_000);
                                    Files.deleteIfExists(tempFile);
                                } catch (Exception ignored) {
                                }
                            });
                        } catch (Exception ex) {
                            logger.error("Erro ao abrir conteudo no navegador: {}", ex.getMessage());
                        }
                    });

                    HBox buttonBar = new HBox(openInBrowserButton);
                    buttonBar.setAlignment(Pos.CENTER_RIGHT);
                    buttonBar.setPadding(new Insets(5, 0, 0, 0));

                    VBox root = new VBox(webView, buttonBar);
                    root.setPadding(new Insets(5));
                    Scene scene = new Scene(root);
                    stage.setScene(scene);

                    stage.show();
                    stage.toFront();
                    stage.requestFocus();

                } catch (Exception ex) {
                    logger.error("Erro ao exibir conteudo em JavaFX: {}", ex.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            logger.info("Erro ao fazer parse do JSON: {}", e.getMessage());
            FxJavaInitializer.ensureInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.NONE);
                stage.setAlwaysOnTop(true);
                stage.setResizable(true);
                stage.centerOnScreen();
                Image icon = FxJavaInitializer.loadFxIcon();
                if (icon != null)
                    stage.getIcons().add(icon);
                stage.setTitle("Mensagem");
                logger.info("Creating fallback HTML window");
                WebView webView = new WebView();
                webView.getEngine().setJavaScriptEnabled(false);
                webView.setContextMenuEnabled(false);
                webView.getEngine().loadContent(
                        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                                + "<style>body{font-family:Arial,sans-serif;margin:10px;white-space:pre-wrap;word-wrap:break-word;}</style>"
                                + "</head><body>"
                                + sanitizeHtml(br.gov.pb.der.netnotifyagent.utils.Functions.stripEmoji(htmlContent))
                                + "</body></html>",
                        "text/html");
                webView.setPrefSize(600, 200);
                VBox root = new VBox(webView);
                root.setPadding(new Insets(5));
                stage.setScene(new Scene(root));
                stage.show();
                stage.toFront();
                stage.requestFocus();
                logger.info("Fallback window should be visible now");
            });
        }
    }

    /**
     * Remove scripts, iframes, meta-refresh e atributos de evento para evitar
     * que o conteudo carregue recursos externos ou injete botoes de recarga.
     */
    public static String sanitizeHtml(String html) {
        if (html == null)
            return "";
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?is)<iframe[^>]*>.*?</iframe>", "");
        html = html.replaceAll("(?is)<embed[^>]*>", "");
        html = html.replaceAll("(?is)<object[^>]*>.*?</object>", "");
        html = html.replaceAll("(?i)<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh['\"]?[^>]*>", "");
        html = html.replaceAll("(?i)on[a-z]+\\s*=\\s*(['\"]).*?\\1", "");
        html = html.replaceAll("(?i)href\\s*=\\s*(['\"])javascript:[^'\"]*\\1", "");
        return html;
    }
}
