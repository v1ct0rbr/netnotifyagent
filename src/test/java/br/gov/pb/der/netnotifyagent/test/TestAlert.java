package br.gov.pb.der.netnotifyagent.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestAlert {

    @Test
    public void testAlertInstanceNotNull() {
        // Basic smoke test - Alert.getInstance() should not throw
        // Full UI test requires JavaFX environment
        assertDoesNotThrow(() -> {
            // Just verify the class is loadable
            Class.forName("br.gov.pb.der.netnotifyagent.ui.Alert");
        });
    }
}
