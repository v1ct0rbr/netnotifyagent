# Pacote de Reparo via ESET

Arquivos necessarios para distribuicao:

- `fix-java-home.bat`
- `run.bat.template`
- `run-hidden.vbs.template`
- `execute-fix.cmd`

## Estrutura sugerida no cliente

```text
%ProgramData%\NetNotifyFix\
  fix-java-home.bat
  run.bat.template
  run-hidden.vbs.template
  execute-fix.cmd
```

## Comando para a tarefa no ESET

```bat
cmd.exe /c "%ProgramData%\NetNotifyFix\execute-fix.cmd"
```

## Comando com caminho customizado

```bat
cmd.exe /c "%ProgramData%\NetNotifyFix\execute-fix.cmd" "F:\Programas"
```

## Log local no cliente

O wrapper grava log em:

```text
%ProgramData%\NetNotifyAgent\fix-java-home.log
```

## Codigo de saida

- `0`: reparo concluido
- `1`: falha no reparo

## Observacoes

- O script nao pausa por padrao.
- Execute com privilegios administrativos.
- O `execute-fix.cmd` usa `F:\Programas` como padrao, mas aceita caminho alternativo como argumento.
