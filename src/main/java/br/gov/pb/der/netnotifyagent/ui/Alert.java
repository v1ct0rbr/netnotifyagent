package br.gov.pb.der.netnotifyagent.ui;

import java.awt.Dimension;

import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.gov.pb.der.netnotifyagent.dto.Message;

public class Alert {

    private static Alert instance;
    private final ObjectMapper objectMapper;

    private Alert() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Se falhar, ignora e usa o padrão
        }
        objectMapper = new ObjectMapper();
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
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, msg.getContent(), "Alerta", JOptionPane.INFORMATION_MESSAGE);
            });
        } catch (Exception e) {
            // Se não conseguir fazer parse do JSON, mostra a mensagem original
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, message, "Alerta", JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }

    public void showError(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, message, "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }

    public void showHtml(String htmlContent) {
        try {
            Message message = objectMapper.readValue(htmlContent, Message.class);
            System.out.println("Level: " + message.getLevel());
            System.out.println("Content: " + message.getContent());

            SwingUtilities.invokeLater(() -> {
                JEditorPane editorPane = new JEditorPane("text/html", message.getContent());
                editorPane.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(editorPane);
                scrollPane.setPreferredSize(new Dimension(400, 300));
                JOptionPane.showMessageDialog(null, scrollPane,
                        message.getType() != null ? message.getType() : "Mensagem",
                        JOptionPane.INFORMATION_MESSAGE);
            });
        } catch (JsonProcessingException e) {
            System.out.println("Erro ao fazer parse do JSON: " + e.getMessage());
            // Se não conseguir fazer parse do JSON, mostra a mensagem original como texto
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, htmlContent, "Mensagem", JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }
}
