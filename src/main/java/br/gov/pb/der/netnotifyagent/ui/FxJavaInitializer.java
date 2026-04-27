package br.gov.pb.der.netnotifyagent.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;

/**
 * Helper to initialize JavaFX toolkit once for non-JavaFX applications (like AWT tray apps).
 */
public final class FxJavaInitializer {

    private static final Logger logger = LoggerFactory.getLogger(FxJavaInitializer.class);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private FxJavaInitializer() {}

    /**
     * Ensure JavaFX toolkit is started. Safe to call multiple times.
     */
    public static void ensureInitialized() {
        if (initialized.get()) return;
        synchronized (initialized) {
            if (initialized.get()) return;
            try {
                CountDownLatch latch = new CountDownLatch(1);
                // Platform.startup can only be called once. It starts the JavaFX toolkit.
                // setImplicitExit(false) is set on the FX thread to guarantee it takes effect
                // before any window could be opened and closed.
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    latch.countDown();
                });
                // Block until the FX Application Thread is actually running.
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    logger.warn("JavaFX toolkit startup timed out after 10 seconds");
                }
                initialized.set(true);
                logger.info("JavaFX toolkit initialized (FxJavaInitializer)");
            } catch (IllegalStateException e) {
                // FX toolkit was already started (e.g. started by another code path).
                // Ensure setImplicitExit is still applied.
                Platform.setImplicitExit(false);
                initialized.set(true);
                logger.info("JavaFX toolkit already running, applied setImplicitExit(false)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting for JavaFX toolkit startup");
            } catch (Exception e) {
                logger.error("Failed to initialize JavaFX toolkit: {}", e.getMessage());
            }
        }
    }

    /** @deprecated Use {@link #ensureInitialized()} instead. */
    @Deprecated
    public static void init() {
        ensureInitialized();
    }

    public static javafx.scene.image.Image loadFxIcon() {
        try (java.io.InputStream is = FxJavaInitializer.class.getResourceAsStream(br.gov.pb.der.netnotifyagent.utils.Constants.ICON_48PX_PATH)) {
            if (is == null) return null;
            return new javafx.scene.image.Image(is);
        } catch (Exception e) {
            return null;
        }
    }

    public static java.awt.Image loadAwtIcon() {
        try (java.io.InputStream is = FxJavaInitializer.class.getResourceAsStream(br.gov.pb.der.netnotifyagent.utils.Constants.ICON_48PX_PATH)) {
            if (is == null) return null;
            byte[] data = is.readAllBytes();
            return java.awt.Toolkit.getDefaultToolkit().createImage(data);
        } catch (Exception e) {
            return null;
        }
    }
}
