package br.gov.pb.der.netnotifyagent.ui;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(TrayService.class);

    private final TrayIcon trayIcon;
    private final RabbitmqService rabbitmqService;
    private final Timer updateTimer = new Timer(true);

    private JFrame popupHelper;
    private JPopupMenu jPopupMenu;

    private java.awt.Image connectedIcon;
    private java.awt.Image disconnectedIcon;
    private volatile boolean lastConnectedState = false;

    public TrayService(RabbitmqService rabbitmqService) throws AWTException {
        this.rabbitmqService = rabbitmqService;

        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException("System tray not supported on this platform");
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image image = FxJavaInitializer.loadAwtIcon();
        if (image == null) {
            image = java.awt.Toolkit.getDefaultToolkit().createImage(new byte[0]);
        }

        this.connectedIcon = image;
        this.disconnectedIcon = createGrayIcon(image);

        trayIcon = new TrayIcon(image, Constants.TRAY_ICON_TOOLTIP);
        trayIcon.setImageAutoSize(true);

        // Build Swing popup on the EDT — JFrame/JPopupMenu must be created there
        try {
            java.awt.EventQueue.invokeAndWait(this::buildPopupMenu);
        } catch (Exception e) {
            logger.error("Falha ao construir menu de tray no EDT: {}", e.getMessage());
        }

        trayIcon.addActionListener(e -> logger.info("Tray clicked.\n{}", rabbitmqService.getSummary()));

        tray.add(trayIcon);

        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateTooltip();
            }
        }, 0, 3000);
    }

    /**
     * Builds the Swing popup menu and attaches it to the tray icon via a MouseListener.
     * Must be called on the AWT/Swing EDT.
     *
     * Using JPopupMenu + hidden helper JFrame instead of the native AWT PopupMenu avoids
     * the Windows focus-negotiation delay that causes the native menu to feel stuck.
     */
    private void buildPopupMenu() {
        jPopupMenu = new JPopupMenu();

        JMenuItem statusItem  = new JMenuItem("Status de Conexao");
        JMenuItem filterItem  = new JMenuItem("Filtros de Mensagens");
        JMenuItem historyItem = new JMenuItem("Historico de Notificacoes");
        JMenuItem configItem  = new JMenuItem("Configuracoes");
        JMenuItem aboutItem   = new JMenuItem("Sobre");
        JMenuItem exitItem    = new JMenuItem("Sair");

        statusItem.addActionListener(e -> {
            try {
                FxJavaInitializer.ensureInitialized();
                showConnectionStatusWindow();
            } catch (Exception ex) {
                trayIcon.displayMessage("NetNotify", "Erro ao abrir status: " + ex.getMessage(),
                        TrayIcon.MessageType.ERROR);
            }
        });

        filterItem.addActionListener(e -> {
            try {
                FxJavaInitializer.ensureInitialized();
                showFilterPreferencesWindow();
            } catch (Exception ex) {
                trayIcon.displayMessage("NetNotify", "Erro ao abrir filtros: " + ex.getMessage(),
                        TrayIcon.MessageType.ERROR);
            }
        });

        historyItem.addActionListener(e -> {
            try {
                FxJavaInitializer.ensureInitialized();
                new HistoryWindow().show();
            } catch (Exception ex) {
                trayIcon.displayMessage("NetNotify", "Erro ao abrir historico: " + ex.getMessage(),
                        TrayIcon.MessageType.ERROR);
            }
        });

        configItem.addActionListener(e -> {
            try {
                FxJavaInitializer.ensureInitialized();
                showConfigurationWindow();
            } catch (Exception ex) {
                trayIcon.displayMessage("NetNotify", "Erro ao abrir configuracoes: " + ex.getMessage(),
                        TrayIcon.MessageType.ERROR);
            }
        });

        aboutItem.addActionListener(e -> {
            try {
                String appInfo = Constants.getAppInfo();
                String javaInfo = JavaRuntimeInfo.getSummary();
                String rabbitmqSummary = rabbitmqService.getSummary();
                showAboutWindow(appInfo + "\n\n" + javaInfo + "\n\n" + rabbitmqSummary);
            } catch (Exception ex) {
                trayIcon.displayMessage("NetNotify", "Nao foi possivel abrir Sobre",
                        TrayIcon.MessageType.ERROR);
            }
        });

        exitItem.addActionListener(e -> {
            try {
                rabbitmqService.stop();
            } catch (Exception ex) {
                // ignore
            }
            shutdown();
            System.exit(0);
        });

        jPopupMenu.add(statusItem);
        jPopupMenu.addSeparator();
        jPopupMenu.add(filterItem);
        jPopupMenu.add(historyItem);
        jPopupMenu.add(configItem);
        jPopupMenu.add(aboutItem);
        jPopupMenu.addSeparator();
        jPopupMenu.add(exitItem);

        // Tiny invisible helper window used to anchor the Swing popup on Windows
        popupHelper = new JFrame();
        popupHelper.setSize(1, 1);
        popupHelper.setUndecorated(true);
        popupHelper.setAlwaysOnTop(true);
        popupHelper.setType(Window.Type.UTILITY);

        jPopupMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { popupHelper.setVisible(false); }
            @Override public void popupMenuCanceled(PopupMenuEvent e)            { popupHelper.setVisible(false); }
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e)   {}
        });

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            @Override public void mousePressed(MouseEvent e)  { maybeShowPopup(e); }
        });
    }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger() || jPopupMenu.isVisible()) return;
        SwingUtilities.invokeLater(() -> {
            int x = e.getX();
            int y = e.getY() - jPopupMenu.getPreferredSize().height;
            popupHelper.setLocation(x, y);
            popupHelper.setVisible(true);
            jPopupMenu.show(popupHelper, 0, 0);
            popupHelper.toFront();
        });
    }

    private java.awt.Image createGrayIcon(java.awt.Image original) {
        try {
            BufferedImage bi = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = bi.createGraphics();
            g.drawImage(original, 0, 0, 48, 48, null);
            g.dispose();
            ColorConvertOp op = new ColorConvertOp(
                java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY), null);
            op.filter(bi, bi);
            return bi;
        } catch (Exception e) {
            return original;
        }
    }

    private void updateTooltip() {
        try {
            boolean connected = rabbitmqService.isConnected();
            String status = rabbitmqService.getStatus();
            String tooltip = "NetNotify Agent\nStatus: " + status;
            boolean iconChanged = connected != lastConnectedState;
            if (iconChanged) {
                lastConnectedState = connected;
            }
            java.awt.Image newIcon = connected ? connectedIcon : disconnectedIcon;
            java.awt.EventQueue.invokeLater(() -> {
                trayIcon.setToolTip(tooltip);
                if (iconChanged) {
                    trayIcon.setImage(newIcon);
                }
            });
        } catch (Exception e) { /* ignore */ }
    }

    public void shutdown() {
        updateTimer.cancel();
        try {
            SystemTray.getSystemTray().remove(trayIcon);
        } catch (Exception e) {
            // ignore
        }
        if (popupHelper != null) {
            SwingUtilities.invokeLater(() -> popupHelper.dispose());
        }
    }

    private void showAboutWindow(String about) {
        FxJavaInitializer.ensureInitialized();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Sobre");

            javafx.scene.image.Image icon = FxJavaInitializer.loadFxIcon();
            if (icon != null) stage.getIcons().add(icon);

            TextArea ta = new TextArea(about);
            ta.setWrapText(true);
            ta.setEditable(false);
            ta.setPrefSize(Integer.parseInt(Constants.STATUS_WINDOW_PREF_WIDTH),
                    Integer.parseInt(Constants.STATUS_WINDOW_PREF_HEIGHT));
            ta.setStyle(Constants.STATUS_WINDOW_TEXTAREA_STYLE);

            VBox root = new VBox(ta);
            root.setPadding(new javafx.geometry.Insets(10));
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        });
    }

    private void showFilterPreferencesWindow() {
        Platform.runLater(() -> {
            FilterPreferencesWindow window = new FilterPreferencesWindow();
            window.show();
        });
    }

    private void showConfigurationWindow() {
        FxJavaInitializer.ensureInitialized();
        Platform.runLater(() -> {
            ConfigurationWindow window = new ConfigurationWindow();
            window.setOnConfigurationSaved(() -> {
                try {
                    Thread.sleep(500);
                    rabbitmqService.restart();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            window.show();
        });
    }

    private void showConnectionStatusWindow() {
        FxJavaInitializer.ensureInitialized();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.initModality(Modality.NONE);
            stage.setTitle("Status de Conexao");

            javafx.scene.image.Image icon = FxJavaInitializer.loadFxIcon();
            if (icon != null) stage.getIcons().add(icon);

            TextArea ta = new TextArea();
            ta.setWrapText(true);
            ta.setEditable(false);
            ta.setPrefSize(Integer.parseInt(Constants.STATUS_WINDOW_PREF_WIDTH),
                    Integer.parseInt(Constants.STATUS_WINDOW_PREF_HEIGHT));
            ta.setStyle(Constants.STATUS_WINDOW_TEXTAREA_STYLE);

            Timer statusUpdateTimer = new Timer(true);
            statusUpdateTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        try {
                            ta.setText(rabbitmqService.getSummary());
                        } catch (Exception e) {
                            // ignore
                        }
                    });
                }
            }, 0, 2000);

            stage.setOnCloseRequest(event -> statusUpdateTimer.cancel());

            VBox root = new VBox(ta);
            root.setPadding(new javafx.geometry.Insets(10));
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        });
    }
}
