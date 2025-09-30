package br.gov.pb.der.netnotifyagent.ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException; // initializes toolkit when running from non-JFX app
import com.fasterxml.jackson.databind.ObjectMapper;

import br.gov.pb.der.netnotifyagent.dto.Message;
import br.gov.pb.der.netnotifyagent.utils.Constants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Alert {

    private static Alert instance;
    private final ObjectMapper objectMapper;
    // indicador para inicializar o toolkit JavaFX apenas uma vez
    private static final AtomicBoolean fxInitialized = new AtomicBoolean(false);

    private Alert() {
        // Não usar Swing LookAndFeel quando rodamos com JavaFX
        objectMapper = new ObjectMapper();
        // Permite JSON usando aspas simples, por exemplo: {'content':'...'}
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    private void ensureFxInitialized() {
        if (fxInitialized.get()) return;
        synchronized (fxInitialized) {
            if (fxInitialized.get()) return;
            try {
                // Use centralized initializer
                br.gov.pb.der.netnotifyagent.ui.FxJavaInitializer.init();
                fxInitialized.set(true);
            } catch (Exception e) {
                System.err.println("Failed to initialize JavaFX via FxJavaInitializer: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static Alert getInstance() {
        if (instance == null) {
            synchronized (Alert.class) {
                if (instance == null) {
                    instance = new Alert();
                }
            }
        }
        return instance;
    }

    public void showInfo(String message) {
        try {
            Message msg = objectMapper.readValue(message, Message.class);
            ensureFxInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.NONE);
                stage.setAlwaysOnTop(true);
                Image icon = loadFxIcon();
                if (icon != null) stage.getIcons().add(icon);
                stage.setTitle("Alerta");
                Text txt = new Text(msg.getContent());
                txt.wrappingWidthProperty().set(600);
                ScrollPane sp = new ScrollPane(txt);
                sp.setPrefSize(600, 200);
                VBox root = new VBox(sp);
                root.setPadding(new Insets(10));
                stage.setScene(new Scene(root));
                stage.show();
                stage.toFront();
            });
        } catch (Exception e) {
            // Se não conseguir fazer parse do JSON, mostra a mensagem original
            ensureFxInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                Image icon = loadFxIcon();
                if (icon != null) stage.getIcons().add(icon);
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
        ensureFxInitialized();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.NONE);
            stage.setAlwaysOnTop(true);
            Image icon = loadFxIcon();
            if (icon != null) stage.getIcons().add(icon);
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
            System.out.println("Level: " + message.getLevel());
            System.out.println("Content: " + message.getContent());
            // Inicializa JavaFX toolkit se necessário
            ensureFxInitialized();

            // WebView consegue carregar imagens remotas nativamente; não é necessário inline
            final String contentToLoad = message.getContent();

            // Mostra a janela de forma não-bloqueante para não travar o consumidor de mensagens
            Platform.runLater(() -> {
                try {
                    Stage stage = new Stage();
                    stage.initModality(Modality.NONE);
                    stage.setAlwaysOnTop(true);
                    stage.setResizable(true);
                    
                    // Força posição específica na tela
                    stage.setX(100);
                    stage.setY(100);
                    stage.setWidth(800);
                    stage.setHeight(500);
                    
                    Image icon = loadFxIcon();
                    if (icon != null) stage.getIcons().add(icon);
                    stage.setTitle(message.getType() != null ? message.getType() : "Mensagem");
                    System.out.println("Creating HTML window: " + stage.getTitle());

                    WebView webView = new WebView();
                    webView.setPrefSize(800, 500);
                    WebEngine engine = webView.getEngine();
                    engine.loadContent(contentToLoad, "text/html");

                    VBox root = new VBox(webView);
                    root.setPadding(new Insets(5));
                    Scene scene = new Scene(root);
                    stage.setScene(scene);

                    // Força a exibição da janela
                    stage.show();
                    stage.toFront();
                    stage.requestFocus();
                    
                    // Adiciona um delay para garantir que a janela seja renderizada
                    Platform.runLater(() -> {
                        stage.toFront();
                        stage.setIconified(false);
                        System.out.println("Window forced to front. Position: " + stage.getX() + "," + stage.getY() + " Size: " + stage.getWidth() + "x" + stage.getHeight());
                    });
                    
                    System.out.println("Window should be visible now");
                } catch (Exception ex) {
                    System.err.println("Erro ao exibir conteúdo em JavaFX: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        } catch (JsonProcessingException e) {
            System.out.println("Erro ao fazer parse do JSON: " + e.getMessage());
            // Se não conseguir fazer parse do JSON, mostra a mensagem original como texto
            ensureFxInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.NONE);
                stage.setAlwaysOnTop(true);
                stage.setResizable(true);
                stage.centerOnScreen();
                Image icon = loadFxIcon();
                if (icon != null) stage.getIcons().add(icon);
                stage.setTitle("Mensagem");
                System.out.println("Creating fallback HTML window");
                WebView webView = new WebView();
                webView.getEngine().loadContent("<pre>" + htmlContent + "</pre>", "text/html");
                webView.setPrefSize(600, 200);
                VBox root = new VBox(webView);
                root.setPadding(new Insets(5));
                stage.setScene(new Scene(root));
                stage.show();
                stage.toFront();
                stage.requestFocus();
                System.out.println("Fallback window should be visible now");
            });
        }
    }

    private Image loadFxIcon() {
        try (InputStream is = Alert.class.getResourceAsStream(Constants.ICON_48PX_PATH)) {
            if (is == null) return null;
            return new Image(is);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Substitui <img src="http(s)://..."> por data URIs base64 quando possível.
     * Em caso de falha no download, mantém a tag original.
     */
    private String inlineRemoteImages(String html) {
        if (html == null || html.isEmpty()) return html;
        Pattern p = Pattern.compile("<img\\s+[^>]*src\\s*=\\s*\"(https?://[^\"\\s>]+)\"[^>]*>", Pattern.CASE_INSENSITIVE);
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
                    if (contentType == null) contentType = "image/png";
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    String dataUri = "data:" + contentType + ";base64," + base64;
                    String originalTag = m.group(0);
                    String replacement = originalTag.replace(urlStr, dataUri);
                    m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
            } catch (Exception ex) {
                // não conseguiu baixar, deixa a tag original
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
