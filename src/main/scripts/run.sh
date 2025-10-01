#!/bin/bash

echo "==============================================="
echo "         NetNotify Agent v${project.version}"
echo "==============================================="
echo ""

# Diretório do script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$SCRIPT_DIR/${project.artifactId}-${project.version}.jar"
LIBS_DIR="$SCRIPT_DIR/libs"

# Verificar se Java está disponível
if ! command -v java &> /dev/null; then
    echo "[ERRO] Java não encontrado!"
    echo ""
    echo "Este aplicativo requer Java 11 ou superior."
    echo "Instale o Java em: https://adoptium.net/temurin/releases"
    echo ""
    exit 1
fi

# Verificar versão do Java
JAVA_VERSION=$(java -version 2>&1 | grep "version" | cut -d'"' -f2)
JAVA_MAJOR=$(echo $JAVA_VERSION | cut -d'.' -f1)

if [ "$JAVA_MAJOR" -lt 11 ]; then
    echo "[ERRO] Versão do Java muito antiga!"
    echo "Encontrado: Java $JAVA_VERSION"
    echo "Necessário: Java 11 ou superior"
    echo ""
    echo "Baixe a versão mais recente em: https://adoptium.net/temurin/releases"
    exit 1
fi

# Verificar se JAR existe
if [ ! -f "$JAR_FILE" ]; then
    echo "[ERRO] Arquivo principal não encontrado!"
    echo "Procurando: $JAR_FILE"
    echo ""
    echo "Certifique-se de que todos os arquivos foram extraídos corretamente."
    exit 1
fi

# Verificar se diretório libs existe
if [ ! -d "$LIBS_DIR" ]; then
    echo "[ERRO] Diretório de dependências não encontrado!"
    echo "Procurando: $LIBS_DIR"
    echo ""
    echo "Certifique-se de que a pasta 'libs' está presente."
    exit 1
fi

echo "[INFO] Java detectado: $JAVA_VERSION"
echo "[INFO] JAR: $JAR_FILE"
echo "[INFO] Libs: $LIBS_DIR"
echo ""
echo "Iniciando NetNotify Agent..."
echo ""

# Executar aplicação
java --module-path "$LIBS_DIR" --add-modules javafx.controls,javafx.web -cp "$JAR_FILE:$LIBS_DIR/*" br.gov.pb.der.netnotifyagent.NetnotifyagentLauncher

# Verificar resultado
EXIT_CODE=$?
if [ $EXIT_CODE -eq 0 ]; then
    echo ""
    echo "[INFO] NetNotify Agent encerrado normalmente."
else
    echo ""
    echo "[ERRO] A aplicação foi encerrada com código de erro: $EXIT_CODE"
    echo ""
    echo "Possíveis causas:"
    echo "- Erro de configuração do RabbitMQ"
    echo "- JavaFX não foi inicializado corretamente"
    echo "- Problema de permissões"
    echo ""
fi