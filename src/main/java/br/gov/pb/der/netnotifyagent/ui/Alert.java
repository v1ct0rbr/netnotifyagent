package br.gov.pb.der.netnotifyagent.ui;

import javax.swing.*;
import java.awt.*;

public class Alert {

    private static Alert instance;
    private final JFrame frame;

    private Alert() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Se falhar, ignora e usa o padrão
        }
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setType(Window.Type.UTILITY);
        frame.setLocationRelativeTo(null);
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
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, message, "Alerta", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    public void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, message, "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }

    public void showHtml(String htmlContent, String title, int messageType) {
        SwingUtilities.invokeLater(() -> {
            JEditorPane editorPane = new JEditorPane("text/html", htmlContent);
            editorPane.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(editorPane);
            scrollPane.setPreferredSize(new Dimension(400, 300));
            JOptionPane.showMessageDialog(frame, scrollPane, title, messageType);
        });
    }
}
