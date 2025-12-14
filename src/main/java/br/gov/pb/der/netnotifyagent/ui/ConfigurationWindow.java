package br.gov.pb.der.netnotifyagent.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import br.gov.pb.der.netnotifyagent.utils.Constants;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ConfigurationWindow {

    private Stage stage;
    private final Properties properties;
    private static final String SETTINGS_FILE = "resources/settings.properties";
    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField passwordVisibleField;
    private boolean passwordVisible = false;
    private TextField exchangeField;
    private TextField queueField;
    private TextField routingKeyField;
    private TextField virtualhostField;
    private TextField departmentField;
    private ComboBox<String> notificationModeCombo;
    private Runnable onConfigurationSaved;

    public ConfigurationWindow() {
        this.properties = new Properties();
        loadProperties();
        this.stage = createStage();
    }

    /**
     * Define callback a ser executado após salvar configurações
     */
    public void setOnConfigurationSaved(Runnable callback) {
        this.onConfigurationSaved = callback;
    }

    private void loadProperties() {
        try {
            File settingsFile = new File(SETTINGS_FILE);
            if (settingsFile.exists()) {
                try (FileInputStream fis = new FileInputStream(settingsFile)) {
                    this.properties.load(fis);
                }
            } else {
                System.err.println("Arquivo de configurações não encontrado: " + settingsFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar configurações: " + e.getMessage());
        }
    }

    private Stage createStage() {
        this.stage = new Stage();
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.setTitle("Configurações");
        this.stage.setResizable(false);

        // Ícone da janela
        try (var is = getClass().getResourceAsStream(Constants.ICON_48PX_PATH)) {
            if (is != null) {
                this.stage.getIcons().add(new javafx.scene.image.Image(is));
            }
        } catch (IOException | NullPointerException e) {
            // ignore
        }

        // Grid para campos
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 5;");

        // Campos de configuração com pré-carregamento
        this.hostField = createTextField("rabbitmq.host", "localhost");
        this.portField = createTextField("rabbitmq.port", "5672");
        this.usernameField = createTextField("rabbitmq.username", "guest");
        this.passwordField = createPasswordField("rabbitmq.password", "");
        this.passwordVisibleField = createTextField("rabbitmq.password", "");
        this.passwordVisibleField.setVisible(false);
        this.passwordVisibleField.setManaged(false);
        this.exchangeField = createTextField("rabbitmq.exchange", "");
        this.queueField = createTextField("rabbitmq.queue", "");
        this.routingKeyField = createTextField("rabbitmq.routingkey", "");
        this.virtualhostField = createTextField("rabbitmq.virtualhost", "/");
        this.departmentField = createTextField("agent.department.name", "");

        this.notificationModeCombo = new ComboBox<>();
        this.notificationModeCombo.getItems().addAll("Janela padrão", "Browser");
        String notifMode = properties.getProperty("notifications.display.mode", "window");
        if ("browser".equalsIgnoreCase(notifMode)) {
            this.notificationModeCombo.getSelectionModel().select("Browser");
        } else {
            this.notificationModeCombo.getSelectionModel().select("Janela padrão");
        }

        int row = 0;
        grid.add(new Label("RabbitMQ Host:"), 0, row);
        grid.add(hostField, 1, row++);

        grid.add(new Label("RabbitMQ Port:"), 0, row);
        grid.add(portField, 1, row++);

        grid.add(new Label("Usuário:"), 0, row);
        grid.add(usernameField, 1, row++);

        grid.add(new Label("Senha:"), 0, row);
        HBox passwordBox = new HBox(5);
        Button togglePasswordButton = new Button("👁");
        togglePasswordButton.setPrefWidth(50);
        togglePasswordButton.setPrefHeight(35);
        togglePasswordButton.setStyle("-fx-font-size: 16; -fx-padding: 5;");
        togglePasswordButton.setOnAction(event -> togglePasswordVisibility());
        passwordBox.getChildren().addAll(passwordField, passwordVisibleField, togglePasswordButton);
        grid.add(passwordBox, 1, row++);

        grid.add(new Label("Exchange:"), 0, row);
        grid.add(exchangeField, 1, row++);

        grid.add(new Label("Fila:"), 0, row);
        grid.add(queueField, 1, row++);

        grid.add(new Label("Routing Key:"), 0, row);
        grid.add(routingKeyField, 1, row++);

        grid.add(new Label("Virtual Host:"), 0, row);
        grid.add(virtualhostField, 1, row++);

        grid.add(new Label("Departamento:"), 0, row);
        grid.add(departmentField, 1, row++);

        grid.add(new Label("Abrir notificações em:"), 0, row);
        grid.add(notificationModeCombo, 1, row++);

        // Botões
        Button saveButton = new Button("Salvar");
        Button cancelButton = new Button("Cancelar");
        Button resetButton = new Button("Resetar");
        Button testButton = new Button("Testar Conexão");

        saveButton.setStyle("-fx-font-size: 12; -fx-padding: 8;");
        cancelButton.setStyle("-fx-font-size: 12; -fx-padding: 8;");
        resetButton.setStyle("-fx-font-size: 12; -fx-padding: 8;");
        testButton.setStyle("-fx-font-size: 12; -fx-padding: 8; -fx-text-fill: white; -fx-background-color: #4CAF50;");

        saveButton.setOnAction(event -> {
            try {
                // Sincronizar senha antes de salvar
                syncPasswordField();
                saveConfiguration(hostField, portField, usernameField, passwordField, exchangeField,
                        queueField, routingKeyField, virtualhostField, departmentField);
                showInfo("Configurações salvas com sucesso!\nO serviço será reiniciado com as novas definições.");
                ConfigurationWindow.this.stage.close();

                // Executa callback para reiniciar o serviço
                if (onConfigurationSaved != null) {
                    new Thread(onConfigurationSaved, "config-restart").start();
                }
            } catch (IOException e) {
                showError("Erro ao salvar: " + e.getMessage());
            }
        });

        cancelButton.setOnAction(event -> ConfigurationWindow.this.stage.close());

        resetButton.setOnAction(event -> {
            loadProperties();
            reloadFormFields();
        });

        testButton.setOnAction(event -> testConnection(testButton));

        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.getChildren().addAll(testButton, saveButton, cancelButton, resetButton);

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.getChildren().addAll(grid, buttonBox);

        Scene scene = new Scene(root, 500, 450);
        this.stage.setScene(scene);

        return this.stage;
    }

    private void reloadFormFields() {
        hostField.setText(properties.getProperty("rabbitmq.host", "localhost"));
        portField.setText(properties.getProperty("rabbitmq.port", "5672"));
        usernameField.setText(properties.getProperty("rabbitmq.username", "guest"));
        String passwordValue = properties.getProperty("rabbitmq.password", "");
        passwordField.setText(passwordValue);
        passwordVisibleField.setText(passwordValue);
        exchangeField.setText(properties.getProperty("rabbitmq.exchange", ""));
        queueField.setText(properties.getProperty("rabbitmq.queue", ""));
        routingKeyField.setText(properties.getProperty("rabbitmq.routingkey", ""));
        virtualhostField.setText(properties.getProperty("rabbitmq.virtualhost", "/"));
        departmentField.setText(properties.getProperty("agent.department.name", ""));

        String notifMode = properties.getProperty("notifications.display.mode", "window");
        if (notificationModeCombo != null) {
            if ("browser".equalsIgnoreCase(notifMode)) {
                notificationModeCombo.getSelectionModel().select("Browser");
            } else {
                notificationModeCombo.getSelectionModel().select("Janela padrão");
            }
        }
    }

    private TextField createTextField(String key, String defaultValue) {
        TextField field = new TextField();
        String value = properties.getProperty(key, defaultValue);
        field.setText(value);
        field.setPrefWidth(300);
        return field;
    }

    private PasswordField createPasswordField(String key, String defaultValue) {
        PasswordField field = new PasswordField();
        String value = properties.getProperty(key, defaultValue);
        field.setText(value);
        field.setPrefWidth(300);
        return field;
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;

        if (passwordVisible) {
            // Mostrar senha - sincronizar texto e mostrar TextField
            passwordVisibleField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
        } else {
            // Esconder senha - sincronizar texto e mostrar PasswordField
            passwordField.setText(passwordVisibleField.getText());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
        }
    }

    private void syncPasswordField() {
        if (passwordVisible) {
            // Se está mostrando, sincronizar do visível para o PasswordField
            passwordField.setText(passwordVisibleField.getText());
        }
    }

    private void saveConfiguration(TextField... fields) throws IOException {
        String[] keys = { "rabbitmq.host", "rabbitmq.port", "rabbitmq.username", "rabbitmq.password",
                "rabbitmq.exchange", "rabbitmq.queue", "rabbitmq.routingkey", "rabbitmq.virtualhost",
                "agent.department.name" };

        for (int i = 0; i < fields.length && i < keys.length; i++) {
            properties.setProperty(keys[i], fields[i].getText());
        }

        // Modo de exibição das notificações
        if (notificationModeCombo != null) {
            String selected = notificationModeCombo.getSelectionModel().getSelectedItem();
            String mode = "browser".equalsIgnoreCase(selected) ? "browser" : "window";
            properties.setProperty("notifications.display.mode", mode);
        }

        // Salvar para arquivo
        try (var fos = new java.io.FileOutputStream(SETTINGS_FILE)) {
            properties.store(fos, "NetNotify Agent Configuration - Updated " + java.time.LocalDateTime.now());
        }
    }

    private void showInfo(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Sucesso");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void testConnection(Button testButton) {
        // Desabilita botão e muda texto
        testButton.setDisable(true);
        String originalText = testButton.getText();
        testButton.setText("Testando...");

        // Executa teste em thread separada para não congelar UI
        new Thread(() -> {
            try {
                final String host = hostField.getText().trim();
                final int port = Integer.parseInt(portField.getText().trim());
                final String username = usernameField.getText().trim();
                final String password = passwordField.getText().trim();
                final String virtualHost = virtualhostField.getText().trim();
                final String exchange = exchangeField.getText().trim();

                if (host.isEmpty() || username.isEmpty()) {
                    javafx.application.Platform.runLater(() -> {
                        testButton.setDisable(false);
                        testButton.setText(originalText);
                        showError("Host e usuário são obrigatórios!");
                    });
                    return;
                }

                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(host);
                factory.setPort(port);
                factory.setUsername(username);
                factory.setPassword(password);
                factory.setVirtualHost(virtualHost);
                factory.setConnectionTimeout(5000);
                factory.setChannelRpcTimeout(5000);

                try (Connection connection = factory.newConnection();
                        Channel channel = connection.createChannel()) {

                    if (!exchange.isEmpty()) {
                        channel.exchangeDeclarePassive(exchange);
                    }

                    javafx.application.Platform.runLater(() -> {
                        testButton.setDisable(false);
                        testButton.setText(originalText);
                        String message = String.format(
                                "✓ Conexão com RabbitMQ realizada com sucesso!%n%nHost: %s:%d%nUsuário: %s%nVirtual Host: %s%n%s",
                                host, port, username, virtualHost,
                                exchange.isEmpty() ? "" : "Exchange: " + exchange + " ✓");
                        showInfo(message);
                    });
                }
            } catch (NumberFormatException e) {
                javafx.application.Platform.runLater(() -> {
                    testButton.setDisable(false);
                    testButton.setText(originalText);
                    showError("Porta deve ser um número!");
                });
            } catch (IOException | java.util.concurrent.TimeoutException e) {
                final String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                javafx.application.Platform.runLater(() -> {
                    testButton.setDisable(false);
                    testButton.setText(originalText);
                    showError("✗ Erro ao conectar:\n" + errorMsg);
                });
            }
        }, "test-connection").start();
    }

    public void show() {
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }
}
