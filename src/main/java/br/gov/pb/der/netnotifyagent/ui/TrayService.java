package br.gov.pb.der.netnotifyagent.ui;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import br.gov.pb.der.netnotifyagent.service.RabbitmqService;
import br.gov.pb.der.netnotifyagent.utils.Constants;
import br.gov.pb.der.netnotifyagent.utils.JavaRuntimeInfo;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class TrayService {

    private final TrayIcon trayIcon;
    private final RabbitmqService rabbitmqService;
    private final Timer updateTimer = new Timer(true);

    public TrayService(RabbitmqService rabbitmqService) throws AWTException {
        this.rabbitmqService = rabbitmqService;

        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException("System tray not supported on this platform");
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image image = loadImage(Constants.ICON_48PX_PATH);
        if (image == null) {
            image = Toolkit.getDefaultToolkit().createImage(new byte[0]);
        }

        trayIcon = new TrayIcon(image, Constants.TRAY_ICON_TOOLTIP);
        trayIcon.setImageAutoSize(true);
        // Menu de contexto do tray
        PopupMenu popup = new PopupMenu();
        MenuItem filterItem = new MenuItem("Filtros de Mensagens");
        MenuItem aboutItem = new MenuItem("Sobre");
        MenuItem exitItem = new MenuItem("Sair");

        filterItem.addActionListener((ActionEvent e) -> {
            try {
                ensureFxInitialized();
                showFilterPreferencesWindow();
            } catch (Exception ex) {
                trayIcon.displayMessage("NetNotify", "Erro ao abrir filtros: " + ex.getMessage(), TrayIcon.MessageType.ERROR);
            }
        });

        aboutItem.addActionListener((ActionEvent e) -> {
            try {
                String summary = JavaRuntimeInfo.getSummary();
                String about = Constants.getAppInfo();
                about += "\n\n" + summary;

                showAboutWindow(about);
            } catch (Exception ex) {
                trayIcon.displayMessage("NetNotify", "Não foi possível abrir Sobre", TrayIcon.MessageType.ERROR);
            }
        });

        exitItem.addActionListener((ActionEvent e) -> {
            try {
                rabbitmqService.stop();
            } catch (Exception ex) {
                // ignore
            }
            shutdown();
            // encerra a JVM
            System.exit(0);
        });

        popup.add(filterItem);
        popup.add(aboutItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);

        // clique padrão também mostra o resumo no console
        trayIcon.addActionListener((ActionEvent e) -> {
            System.out.println("Tray clicked.\n" + rabbitmqService.getSummary());
        });

        tray.add(trayIcon);

        // atualiza tooltip a cada 3 segundos
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateTooltip();
            }
        }, 0, 3000);
    }

    private void updateTooltip() {
        try {
            String summary = rabbitmqService.getSummary();
            // tooltips em tray geralmente têm limite de comprimento; usamos primeira linha como título
            trayIcon.setToolTip(summary);
        } catch (Exception e) {
            // ignore
        }
    }

    private Image loadImage(String path) {
        try (InputStream is = TrayService.class.getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            byte[] data = is.readAllBytes();
            return Toolkit.getDefaultToolkit().createImage(data);
        } catch (Exception e) {
            return null;
        }
    }

    public void shutdown() {
        updateTimer.cancel();
        try {
            SystemTray.getSystemTray().remove(trayIcon);
        } catch (Exception e) {
            // ignore
        }
    }

    // --- JavaFX helpers ---
    private static final AtomicBoolean fxInit = new AtomicBoolean(false);

    private void ensureFxInitialized() {
        if (fxInit.get()) {
            return;
        }
        synchronized (fxInit) {
            if (fxInit.get()) {
                return;
            }
            try {
                br.gov.pb.der.netnotifyagent.ui.FxJavaInitializer.init();
                fxInit.set(true);
            } catch (Exception e) {
                System.err.println("Failed to initialize JavaFX in TrayService: " + e.getMessage());
            }
        }
    }

    private void showAboutWindow(String about) {
        String sumary = JavaRuntimeInfo.getSummary();
        ensureFxInitialized();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Sobre");

            javafx.scene.image.Image icon = loadFxIcon();
            if (icon != null) {
                stage.getIcons().add(icon);
            }

            TextArea ta = new TextArea(about);
            ta.setWrapText(true);
            ta.setEditable(false);
            ta.setPrefSize(Integer.parseInt(Constants.STATUS_WINDOW_PREF_WIDTH), Integer.parseInt(Constants.STATUS_WINDOW_PREF_HEIGHT));
            ta.setStyle(Constants.STATUS_WINDOW_TEXTAREA_STYLE);

            VBox root = new VBox(ta);
            root.setPadding(new javafx.geometry.Insets(10));
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        });
    }

    private javafx.scene.image.Image loadFxIcon() {
        try (InputStream is = TrayService.class.getResourceAsStream(Constants.ICON_48PX_PATH)) {
            if (is == null) {
                return null;
            }
            return new javafx.scene.image.Image(is);
        } catch (Exception e) {
            return null;
        }
    }

    private void showFilterPreferencesWindow() {
        Platform.runLater(() -> {
            FilterPreferencesWindow window = new FilterPreferencesWindow();
            window.show();
        });
    }
}
