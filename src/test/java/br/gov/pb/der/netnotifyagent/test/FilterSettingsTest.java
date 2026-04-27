package br.gov.pb.der.netnotifyagent.test;

import br.gov.pb.der.netnotifyagent.utils.FilterSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FilterSettingsTest {

    @Test
    public void testDefaultLevelsAreEnabled() {
        FilterSettings.setBaixoEnabled(true);
        FilterSettings.setNormalEnabled(true);
        FilterSettings.setAltoEnabled(true);
        assertTrue(FilterSettings.isBaixoEnabled(), "Baixo should be enabled by default");
        assertTrue(FilterSettings.isNormalEnabled(), "Normal should be enabled by default");
        assertTrue(FilterSettings.isAltoEnabled(), "Alto should be enabled by default");
        assertTrue(FilterSettings.isUrgenteEnabled(), "Urgente should always be enabled");
    }

    @Test
    public void testShouldShowMessageWithAllLevels() {
        FilterSettings.setBaixoEnabled(true);
        FilterSettings.setNormalEnabled(true);
        FilterSettings.setAltoEnabled(true);
        assertTrue(FilterSettings.shouldShowMessage("Baixo"));
        assertTrue(FilterSettings.shouldShowMessage("Normal"));
        assertTrue(FilterSettings.shouldShowMessage("Alto"));
        assertTrue(FilterSettings.shouldShowMessage("Urgente"));
        assertTrue(FilterSettings.shouldShowMessage(null));
        assertTrue(FilterSettings.shouldShowMessage("Desconhecido"));
    }

    @Test
    public void testDisableBaixo() {
        FilterSettings.setBaixoEnabled(false);
        assertFalse(FilterSettings.shouldShowMessage("Baixo"));
        FilterSettings.setBaixoEnabled(true); // restore
    }

    @Test
    public void testDisableNormal() {
        FilterSettings.setNormalEnabled(false);
        assertFalse(FilterSettings.shouldShowMessage("Normal"));
        FilterSettings.setNormalEnabled(true); // restore
    }

    @Test
    public void testUrgenteAlwaysEnabled() {
        FilterSettings.setUrgenteEnabled(false); // should be ignored
        assertTrue(FilterSettings.isUrgenteEnabled());
        assertTrue(FilterSettings.shouldShowMessage("Urgente"));
    }

    @Test
    public void testGetSummary() {
        String summary = FilterSettings.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Baixo"));
        assertTrue(summary.contains("Normal"));
        assertTrue(summary.contains("Alto"));
        assertTrue(summary.contains("Urgente"));
    }
}
