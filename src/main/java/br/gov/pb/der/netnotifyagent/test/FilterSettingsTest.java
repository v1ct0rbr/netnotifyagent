package br.gov.pb.der.netnotifyagent.test;

import br.gov.pb.der.netnotifyagent.utils.FilterSettings;

/**
 * Teste simples do sistema de filtros
 * Execute para verificar se os filtros estão funcionando corretamente
 */
public class FilterSettingsTest {

    public static void main(String[] args) {
        System.out.println("=== TESTE DO SISTEMA DE FILTROS ===\n");

        // Teste 1: Configurações padrão
        System.out.println("Teste 1: Carregando configurações padrão");
        System.out.println(FilterSettings.getSummary());
        System.out.println();

        // Teste 2: Verificar cada nível
        System.out.println("Teste 2: Verificando cada nível");
        System.out.println("Nível 'Baixo': " + FilterSettings.isBaixoEnabled());
        System.out.println("Nível 'Normal': " + FilterSettings.isNormalEnabled());
        System.out.println("Nível 'Alto': " + FilterSettings.isAltoEnabled());
        System.out.println("Nível 'Urgente': " + FilterSettings.isUrgenteEnabled());
        System.out.println();

        // Teste 3: shouldShowMessage com diferentes níveis
        System.out.println("Teste 3: Teste de shouldShowMessage");
        testMessage("Baixo", true); // Esperado: true (padrão)
        testMessage("Normal", true); // Esperado: true (padrão)
        testMessage("Alto", true); // Esperado: true (padrão)
        testMessage("Urgente", true); // Esperado: true (padrão)
        testMessage(null, true); // Esperado: true (sem nível definido)
        testMessage("Desconhecido", true); // Esperado: true (nível desconhecido)
        System.out.println();

        // Teste 4: Desativar um nível
        System.out.println("Teste 4: Desativando nível 'Baixo'");
        FilterSettings.setBaixoEnabled(false);
        System.out.println(FilterSettings.getSummary());
        System.out.println("shouldShowMessage('Baixo'): " + FilterSettings.shouldShowMessage("Baixo"));
        System.out.println();

        // Teste 5: Desativar outro nível
        System.out.println("Teste 5: Desativando nível 'Normal'");
        FilterSettings.setNormalEnabled(false);
        System.out.println(FilterSettings.getSummary());
        System.out.println();

        // Teste 6: Salvar e recarregar
        System.out.println("Teste 6: Salvando e recarregando configurações");
        FilterSettings.saveSettings();
        System.out.println("Configurações salvas em resources/settings.properties");
        
        // Restaurar padrões para recarregar
        FilterSettings.setBaixoEnabled(true);
        FilterSettings.setNormalEnabled(true);
        System.out.println("Configurações revertidas para padrão na memória");
        
        // Recarregar do arquivo
        FilterSettings.reload();
        System.out.println("Configurações recarregadas do arquivo:");
        System.out.println(FilterSettings.getSummary());
        System.out.println();

        // Teste 7: Restaurar todos como padrão
        System.out.println("Teste 7: Restaurando padrão (todos ativados)");
        FilterSettings.setBaixoEnabled(true);
        FilterSettings.setNormalEnabled(true);
        FilterSettings.setAltoEnabled(true);
        FilterSettings.setUrgenteEnabled(true);
        FilterSettings.saveSettings();
        System.out.println(FilterSettings.getSummary());
        System.out.println();

        System.out.println("=== FIM DO TESTE ===");
    }

    private static void testMessage(String level, boolean expectedResult) {
        boolean result = FilterSettings.shouldShowMessage(level);
        String status = result == expectedResult ? "✓ PASS" : "✗ FAIL";
        System.out.println(status + " - Nível '" + level + "': " + result);
    }
}
