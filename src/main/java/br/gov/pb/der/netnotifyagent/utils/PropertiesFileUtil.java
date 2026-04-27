package br.gov.pb.der.netnotifyagent.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Utilitário para atualizar arquivos .properties sem usar Properties.store(),
 * preservando linhas não gerenciadas (como java.home e install.path com caminhos
 * Windows) que seriam corrompidas pela interpretação de escape do Properties.load().
 */
public class PropertiesFileUtil {

    private PropertiesFileUtil() {}

    /**
     * Atualiza apenas as chaves especificadas no arquivo .properties,
     * preservando todas as outras linhas exatamente como estão (incluindo
     * comentários e caminhos Windows com barras invertidas).
     *
     * @param file    arquivo .properties a ser atualizado
     * @param updates mapa de chave → novo valor (apenas estas chaves serão alteradas)
     * @throws IOException se ocorrer erro de leitura/escrita
     */
    public static void updateProperties(File file, Map<String, String> updates) throws IOException {
        List<String> lines;
        if (file.exists()) {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } else {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            lines = new ArrayList<>();
        }

        Set<String> written = new HashSet<>();
        List<String> result = new ArrayList<>(lines.size() + updates.size());

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                result.add(line);
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                result.add(line);
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (updates.containsKey(key)) {
                result.add(key + "=" + updates.get(key));
                written.add(key);
            } else {
                result.add(line);
            }
        }

        // Acrescentar chaves novas que não existiam no arquivo
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (!written.contains(entry.getKey())) {
                result.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String line : result) {
                writer.write(line);
                writer.newLine();
            }
        }
    }
}
