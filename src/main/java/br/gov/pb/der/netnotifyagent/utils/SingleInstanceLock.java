package br.gov.pb.der.netnotifyagent.utils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Garante que apenas uma instância do NetNotify Agent esteja em execução
 * por usuário, usando um arquivo de lock no diretório home.
 */
public final class SingleInstanceLock {

    private static FileChannel channel;
    private static FileLock lock;

    private SingleInstanceLock() {
    }

    /**
     * Tenta adquirir o lock de instância.
     *
     * @return true se o lock foi adquirido (pode continuar startando)
     *         false se já existe outra instância em execução
     */
    public static synchronized boolean acquire() {
        if (lock != null && lock.isValid()) {
            return true;
        }

        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".netnotifyagent");
            Files.createDirectories(dir);
            Path lockFile = dir.resolve("instance.lock");

            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                // Já existe um lock nesta JVM / processo
                return false;
            }

            if (lock == null) {
                return false;
            }

            // Garante liberação do lock ao finalizar
            Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceLock::release));
            return true;
        } catch (IOException e) {
            System.err.println("Nao foi possivel criar lock de instancia: " + e.getMessage());
            // Em caso de erro inesperado ao criar o lock, nao impede a aplicacao de rodar
            return true;
        }
    }

    /**
     * Libera o lock (se obtido).
     */
    public static synchronized void release() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
            // ignorar
        }

        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
            // ignorar
        }

        lock = null;
        channel = null;
    }
}
