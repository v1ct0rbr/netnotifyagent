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

## Comando recomendado para a tarefa no ESET

Prefira executar o arquivo `.cmd` diretamente, sem envolver `cmd.exe /c`.

```bat
%ProgramData%\NetNotifyFix\execute-fix.cmd /quiet
```

## Comando com caminho customizado

```bat
%ProgramData%\NetNotifyFix\execute-fix.cmd "F:\Programas" /quiet
```

## Se o ESET exigir `cmd.exe /c`

Use aspas aninhadas corretamente. Uma forma incorreta pode abrir um prompt vazio e não executar o reparo.

```bat
cmd.exe /c ""%ProgramData%\NetNotifyFix\execute-fix.cmd" /quiet"
```

Com caminho customizado:

```bat
cmd.exe /c ""%ProgramData%\NetNotifyFix\execute-fix.cmd" "F:\Programas" /quiet"
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
- Sem ` /quiet`, o wrapper mostra status no console para evitar janela em branco.
- Execute com privilegios administrativos.
- O `execute-fix.cmd` usa `F:\Programas` como padrao, mas aceita caminho alternativo como argumento.
- Se o comando for montado com `cmd.exe /c` e aspas erradas, o Windows pode abrir um prompt em branco em vez de executar o script.
