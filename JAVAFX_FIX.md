# Resolução do Problema de Compilação do JavaFX

## Problema
O projeto falhava ao compilar com erro:
```
Could not resolve dependencies: org.openjfx:javafx-base:jar:${javafx.platform}:22.0.1 (absent)
```

## Causa
O OpenJFX (todas as versões) contém uma dependência transitiva que usa a propriedade `${javafx.platform}` sem defini-la corretamente, causando erro de resolução durante a compilação Maven.

## Solução
A solução foi adicionar um arquivo `.mvn/maven.config` que automaticamente define a propriedade `javafx.platform` para todos os comandos Maven.

### Arquivo: `.mvn/maven.config`
```
-Djavafx.platform=win
```

Este arquivo garante que ao executar qualquer comando Maven (mvn clean, mvn package, etc.), a propriedade `javafx.platform` será automaticamente definida como `win` para compilação no Windows.

## Alterações Realizadas
1. **pom.xml**: 
   - Adicionada propriedade `<javafx.platform>win</javafx.platform>` nas properties
   - Atualizado JavaFX para versão 22.0.1 com classifier `win` explícito

2. **`.mvn/maven.config`**: 
   - Criado arquivo de configuração Maven que passa `-Djavafx.platform=win` automaticamente

## Como Compilar
Agora o projeto pode ser compilado normalmente:
```bash
mvn clean package
```

Não há necessidade de passar parâmetros adicionais, pois o `.mvn/maven.config` cuida disso automaticamente.

## Arquivos Gerados
Após compilação bem-sucedida, você encontrará:
- `target/netnotifyagent-1.0-SNAPSHOT.jar` - JAR executável
- `target/libs/` - Todas as dependências (JAR libraries)
- `Output/NetNotifyAgent-Setup-1.0.0.exe` - Instalador Windows
