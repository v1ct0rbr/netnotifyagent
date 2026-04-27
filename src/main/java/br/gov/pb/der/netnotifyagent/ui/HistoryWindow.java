package br.gov.pb.der.netnotifyagent.ui;

import java.util.List;
import br.gov.pb.der.netnotifyagent.service.NotificationHistory;
import br.gov.pb.der.netnotifyagent.service.NotificationHistory.HistoryEntry;
import br.gov.pb.der.netnotifyagent.utils.Constants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class HistoryWindow {
    private Stage stage;

    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        Platform.runLater(this::createAndShow);
    }

    private void createAndShow() {
        stage = new Stage();
        stage.initModality(Modality.NONE);
        stage.setTitle("Historico de Notificacoes");
        stage.setWidth(700);
        stage.setHeight(500);

        try (java.io.InputStream is = getClass().getResourceAsStream(Constants.ICON_48PX_PATH)) {
            if (is != null) stage.getIcons().add(new javafx.scene.image.Image(is));
        } catch (Exception ignored) {}

        List<HistoryEntry> entries = NotificationHistory.getInstance().getEntries();

        ListView<HistoryEntry> listView = new ListView<>();
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(HistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        listView.getItems().addAll(entries);
        if (!entries.isEmpty()) {
            listView.getSelectionModel().selectLast();
        }

        WebViewPane detail = new WebViewPane();
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                detail.load(selected.htmlContent());
            }
        });

        if (!entries.isEmpty()) {
            detail.load(entries.get(entries.size() - 1).htmlContent());
        }

        Label countLabel = new Label(entries.size() + " notificacao(oes) no historico");
        countLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

        Button clearButton = new Button("Limpar historico");
        clearButton.setOnAction(e -> {
            NotificationHistory.getInstance().clear();
            listView.getItems().clear();
            detail.load("");
            countLabel.setText("0 notificacao(oes) no historico");
        });

        HBox bottom = new HBox(10, countLabel, clearButton);
        bottom.setPadding(new Insets(5));
        bottom.setAlignment(Pos.CENTER_LEFT);

        SplitPane split = new SplitPane(listView, detail.getNode());
        split.setDividerPositions(0.35);

        VBox root = new VBox(split, bottom);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(5));

        stage.setScene(new Scene(root));
        stage.show();
        stage.toFront();
    }

    private static class WebViewPane {
        private final WebView webView;

        WebViewPane() {
            webView = new WebView();
            webView.getEngine().setJavaScriptEnabled(false);
            webView.setContextMenuEnabled(false);
        }

        void load(String content) {
            Platform.runLater(() -> webView.getEngine().loadContent(
                content != null && !content.isEmpty() ? content : "<em>Sem conteudo</em>", "text/html"));
        }

        Node getNode() { return webView; }
    }
}
