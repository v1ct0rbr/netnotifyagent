package br.gov.pb.der.netnotifyagent.service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.gov.pb.der.netnotifyagent.utils.Constants;

public final class NotificationHistory {

    private static final Logger logger = LoggerFactory.getLogger(NotificationHistory.class);
    private static final int MAX_ENTRIES = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final NotificationHistory INSTANCE = new NotificationHistory();

    private final Deque<HistoryEntry> entries = new ArrayDeque<>();

    private NotificationHistory() {
        load();
    }

    public static NotificationHistory getInstance() { return INSTANCE; }

    public synchronized void add(String title, String level, String htmlContent) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.pollFirst();
        }
        entries.addLast(HistoryEntry.of(title, level, htmlContent));
        saveAsync();
    }

    public synchronized List<HistoryEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
        saveAsync();
    }

    private void load() {
        File file = new File(Constants.HISTORY_FILE_PATH);
        if (!file.exists()) return;
        try {
            List<HistoryEntry> loaded = MAPPER.readValue(file, new TypeReference<List<HistoryEntry>>() {});
            entries.addAll(loaded);
            logger.info("Historico de notificacoes carregado: {} entradas", loaded.size());
        } catch (IOException e) {
            logger.warn("Nao foi possivel carregar historico de notificacoes: {}", e.getMessage());
        }
    }

    private void saveAsync() {
        List<HistoryEntry> snapshot = new ArrayList<>(entries);
        Thread t = new Thread(() -> {
            try {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(Constants.HISTORY_FILE_PATH), snapshot);
            } catch (IOException e) {
                logger.warn("Nao foi possivel salvar historico de notificacoes: {}", e.getMessage());
            }
        }, "history-save");
        t.setDaemon(true);
        t.start();
    }

    public record HistoryEntry(String timestamp, String title, String level, String htmlContent) {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

        public HistoryEntry {
            title = title != null ? title : "(sem titulo)";
            level = level != null ? level : "-";
            htmlContent = htmlContent != null ? htmlContent : "";
        }

        static HistoryEntry of(String title, String level, String htmlContent) {
            return new HistoryEntry(LocalDateTime.now().format(FMT), title, level, htmlContent);
        }

        @Override
        public String toString() {
            return "[" + timestamp + "] [" + level + "] " + title;
        }
    }
}
