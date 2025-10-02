package br.gov.pb.der.netnotifyagent.ui;

import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;

/**
 * Helper to initialize JavaFX toolkit once for non-JavaFX applications (like AWT tray apps).
 */
public final class FxJavaInitializer {

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private FxJavaInitializer() {}

    /**
     * Ensure JavaFX toolkit is started. Safe to call multiple times.
     */
    public static void init() {
        if (initialized.get()) return;
        synchronized (initialized) {
            if (initialized.get()) return;
            try {
                // Platform.startup can only be called once. It starts the JavaFX toolkit.
                Platform.startup(() -> {
                    // no-op runnable executed on JavaFX Application Thread
                });
                // prevent JavaFX from exiting the JVM when last window is closed
                Platform.setImplicitExit(false);
                initialized.set(true);
                System.out.println("JavaFX toolkit initialized (FxJavaInitializer)");
            } catch (IllegalStateException e) {
                // already initialized concurrently
                initialized.set(true);
            } catch (Exception e) {
                System.err.println("Failed to initialize JavaFX toolkit: " + e.getMessage());                
            }
        }
    }
}
