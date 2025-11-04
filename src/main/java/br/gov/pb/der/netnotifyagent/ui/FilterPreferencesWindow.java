package br.gov.pb.der.netnotifyagent.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import br.gov.pb.der.netnotifyagent.utils.FilterSettings;

/**
 * Janela de preferências para configurar filtros de mensagens
 */
public class FilterPreferencesWindow {

    private Stage stage;
    private CheckBox checkBaixo;
    private CheckBox checkNormal;
    private CheckBox checkAlto;
    private CheckBox checkUrgente;
    private Label statusLabel;

    public FilterPreferencesWindow() {
        createWindow();
    }

    /**
     * Cria e inicializa a janela de preferências
     */
    private void createWindow() {
        stage = new Stage();
        stage.setTitle("Filtros de Mensagens");
        stage.setWidth(350);
        stage.setHeight(350);
        stage.setResizable(false);

        // Layout principal
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-font-family: Arial; -fx-font-size: 11;");

        // Título
        Label titleLabel = new Label("Selecione quais níveis de mensagens deseja receber:");
        titleLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");
        root.getChildren().add(titleLabel);

        // Separator
        Separator sep1 = new Separator();
        root.getChildren().add(sep1);

        // Checkboxes para cada nível
        checkBaixo = new CheckBox("Mensagens com Nível Baixo");
        checkBaixo.setSelected(FilterSettings.isBaixoEnabled());
        checkBaixo.setStyle("-fx-font-size: 11;");
        root.getChildren().add(checkBaixo);

        checkNormal = new CheckBox("Mensagens com Nível Normal");
        checkNormal.setSelected(FilterSettings.isNormalEnabled());
        checkNormal.setStyle("-fx-font-size: 11;");
        root.getChildren().add(checkNormal);

        checkAlto = new CheckBox("Mensagens com Nível Alto");
        checkAlto.setSelected(FilterSettings.isAltoEnabled());
        checkAlto.setStyle("-fx-font-size: 11;");
        root.getChildren().add(checkAlto);

        checkUrgente = new CheckBox("Mensagens com Nível Urgente");
        checkUrgente.setSelected(FilterSettings.isUrgenteEnabled());
        checkUrgente.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");
        root.getChildren().add(checkUrgente);

        // Separator
        Separator sep2 = new Separator();
        root.getChildren().add(sep2);

        // Status label
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 10; -fx-font-weight: bold;");
        root.getChildren().add(statusLabel);

        // Botões
        HBox buttonsBox = new HBox(10);
        buttonsBox.setPadding(new Insets(10, 0, 0, 0));

        Button btnSalvar = new Button("Salvar");
        btnSalvar.setPrefWidth(100);
        btnSalvar.setStyle("-fx-font-size: 11; -fx-padding: 8px;");
        btnSalvar.setOnAction(e -> onSave());

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setPrefWidth(100);
        btnCancelar.setStyle("-fx-font-size: 11; -fx-padding: 8px;");
        btnCancelar.setOnAction(e -> stage.close());

        Button btnResetarPadrao = new Button("Restaurar Padrão");
        btnResetarPadrao.setPrefWidth(100);
        btnResetarPadrao.setStyle("-fx-font-size: 11; -fx-padding: 8px;");
        btnResetarPadrao.setOnAction(e -> onResetDefault());

        buttonsBox.getChildren().addAll(btnSalvar, btnCancelar, btnResetarPadrao);

        VBox.setVgrow(buttonsBox, javafx.scene.layout.Priority.ALWAYS);
        root.getChildren().add(buttonsBox);

        // Cena
        Scene scene = new Scene(root);
        stage.setScene(scene);

        // Ícone da janela (se disponível)
        try {
            stage.getIcons().add(new javafx.scene.image.Image(
                    getClass().getResourceAsStream("/images/notification.png")));
        } catch (Exception e) {
            System.out.println("[FilterPreferencesWindow] Ícone não encontrado: " + e.getMessage());
        }

        stage.setOnCloseRequest(e -> {
            // Recarregar configurações ao fechar, em caso de mudanças externas
            FilterSettings.reload();
        });
    }

    /**
     * Salva as preferências
     */
    private void onSave() {
        // Atualizar as configurações
        FilterSettings.setBaixoEnabled(checkBaixo.isSelected());
        FilterSettings.setNormalEnabled(checkNormal.isSelected());
        FilterSettings.setAltoEnabled(checkAlto.isSelected());
        FilterSettings.setUrgenteEnabled(checkUrgente.isSelected());

        // Salvar em arquivo
        FilterSettings.saveSettings();

        // Mostrar confirmação
        statusLabel.setText("✓ Configurações salvas com sucesso!");
        statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 10; -fx-font-weight: bold;");

        // Fechar após 1.5 segundos
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                Platform.runLater(() -> stage.close());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        System.out.println("[FilterPreferencesWindow] Preferências salvas:");
        System.out.println(FilterSettings.getSummary());
    }

    /**
     * Restaura os padrões (todos ativados)
     */
    private void onResetDefault() {
        checkBaixo.setSelected(true);
        checkNormal.setSelected(true);
        checkAlto.setSelected(true);
        checkUrgente.setSelected(true);

        statusLabel.setText("Padrões restaurados");
        statusLabel.setStyle("-fx-text-fill: #2196F3; -fx-font-size: 10;");
    }

    /**
     * Exibe a janela
     */
    public void show() {
        if (stage == null) {
            createWindow();
        }
        stage.show();
        stage.toFront();
    }

    /**
     * Verifica se a janela está aberta
     */
    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    /**
     * Fecha a janela
     */
    public void close() {
        if (stage != null) {
            stage.close();
        }
    }
}
