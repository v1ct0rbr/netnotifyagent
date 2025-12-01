package br.gov.pb.der.netnotifyagent.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import br.gov.pb.der.netnotifyagent.utils.MessageUtils;
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

    // 'volatile' ensures visibility of changes to 'instance' across threads for
    // correct double-checked locking
    private static volatile Alert instance;
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
        if (fxInitialized.get()) {
            return;
        }
        synchronized (fxInitialized) {
            if (fxInitialized.get()) {
                return;
            }
            try {
                // Use centralized initializer
                br.gov.pb.der.netnotifyagent.ui.FxJavaInitializer.init();
                fxInitialized.set(true);
            } catch (Exception e) {
                System.err.println("Failed to initialize JavaFX via FxJavaInitializer: " + e.getMessage());
            }
        }
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
            ensureFxInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.NONE);
                stage.setAlwaysOnTop(true);
                Image icon = loadFxIcon();
                if (icon != null) {
                    stage.getIcons().add(icon);
                }
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
        } catch (JsonProcessingException e) {
            // Se não conseguir fazer parse do JSON, mostra a mensagem original
            ensureFxInitialized();
            Platform.runLater(() -> {
                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                Image icon = loadFxIcon();
                if (icon != null) {
                    stage.getIcons().add(icon);
                }
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
            if (icon != null) {
                stage.getIcons().add(icon);
            }
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
            System.out.println("Parsed message: " + message.getContent());

            // Inicializa JavaFX toolkit se necessário
            ensureFxInitialized();

            final String contentToLoad = sanitizeHtml(addHtmlTagsToContent(message));

            Platform.runLater(() -> {
                try {
                    Stage stage = new Stage();
                    stage.initModality(Modality.NONE);
                    stage.setAlwaysOnTop(true);
                    stage.setResizable(true);

                    Image icon = loadFxIcon();
                    if (icon != null) {
                        stage.getIcons().add(icon);
                    }
                    stage.setTitle(
                            message.getType() != null ? MessageUtils.formatMessageType(message.getType()) : "Mensagem");

                    WebView webView = new WebView();
                    WebEngine engine = webView.getEngine();

                    // Desativa JavaScript e menu de contexto para evitar scripts externos que criem
                    // botões
                    engine.setJavaScriptEnabled(false);
                    webView.setContextMenuEnabled(false);

                    // limites máximos (90% da area visivel)
                    double maxW = javafx.stage.Screen.getPrimary().getVisualBounds().getWidth() * 0.9;
                    double maxH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() * 0.9;

                    // registra listener de erros de carga para fallback
                    engine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
                        if (newEx != null) {
                            System.err.println("WebEngine load error: " + newEx.getMessage());
                        }
                    });

                    // carga do conteúdo
                    engine.loadContent(contentToLoad, "text/html");

                    // quando o documento terminar de carregar, obter tamanho e ajustar janela
                    engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        try {
                            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                                // executa JS apenas para medir (JS está desativado, usar valores padrões
                                // seguros)
                                Object wObj = engine.executeScript(
                                        "Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)");
                                Object hObj = engine.executeScript(
                                        "Math.max(document.documentElement.scrollHeight, document.body.scrollHeight)");

                                double contentW = (wObj instanceof Number) ? ((Number) wObj).doubleValue() : 800;
                                double contentH = (hObj instanceof Number) ? ((Number) hObj).doubleValue() : 600;

                                // aplicar padding e limites
                                double paddingW = 20;
                                double paddingH = 80; // inclui barras/titulo
                                double finalW = Math.min(contentW + paddingW, maxW);
                                double finalH = Math.min(contentH + paddingH, maxH);

                                // ajustar WebView e Stage
                                webView.setPrefWidth(finalW);
                                webView.setPrefHeight(finalH);
                                stage.setWidth(finalW);
                                stage.setHeight(finalH);

                                // opcional: garantir que a janela não fique menor que um mínimo
                                stage.setMinWidth(300);
                                stage.setMinHeight(150);

                                // centralizar/visibilizar
                                stage.centerOnScreen();
                                stage.toFront();
                            }
                        } catch (Exception ex) {
                            System.err.println("Erro ao ajustar tamanho dinamico: " + ex.getMessage());
                        }
                    });

                    VBox root = new VBox(webView);
                    root.setPadding(new Insets(5));
                    Scene scene = new Scene(root);
                    stage.setScene(scene);

                    // mostra imediatamente; será redimensionada no listener acima
                    stage.show();
                    stage.toFront();
                    stage.requestFocus();

                } catch (Exception ex) {
                    System.err.println("Erro ao exibir conteúdo em JavaFX: " + ex.getMessage());
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
                if (icon != null) {
                    stage.getIcons().add(icon);
                }
                stage.setTitle("Mensagem");
                System.out.println("Creating fallback HTML window");
                WebView webView = new WebView();
                webView.getEngine().setJavaScriptEnabled(false);
                webView.setContextMenuEnabled(false);
                webView.getEngine().loadContent("<pre>" + sanitizeHtml(htmlContent) + "</pre>", "text/html");
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
            if (is == null) {
                return null;
            }
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

    // Adiciona tags HTML básicas se não existirem
    // Usar font roboto ou similar para melhor compatibilidade
    public String addHtmlTagsToContent(Message message) {
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

    /**
     * Remove scripts, iframes, meta-refresh e atributos de evento (onerror/onload
     * etc.)
     * para evitar que o conteúdo carregue recursos externos ou injete botões de
     * recarga.
     */
    private String sanitizeHtml(String html) {
        if (html == null)
            return "";

        // remove <script>...</script>
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");

        // remove <iframe>...</iframe> e tags embed/object
        html = html.replaceAll("(?is)<iframe[^>]*>.*?</iframe>", "");
        html = html.replaceAll("(?is)<embed[^>]*>", "");
        html = html.replaceAll("(?is)<object[^>]*>.*?</object>", "");

        // remove meta refresh
        html = html.replaceAll("(?i)<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh['\"]?[^>]*>", "");

        // remove inline event handlers (onerror, onload, onclick, etc.)
        html = html.replaceAll("(?i)on[a-z]+\\s*=\\s*(['\"]).*?\\1", "");

        // remove javascript: URLs
        html = html.replaceAll("(?i)href\\s*=\\s*(['\"])javascript:[^'\"]*\\1", "");

        return html;
    }
}
