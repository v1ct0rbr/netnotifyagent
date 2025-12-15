' Script para executar run.bat sem mostrar a janela do console
' Obtém o diretório onde o script VBS está localizado

Dim objShell, objFSO, strScriptDir, strBatchFile, strCmd

Set objShell = CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

' Obtém o diretório do arquivo VBS (que está no mesmo diretório que run.bat)
strScriptDir = objFSO.GetParentFolderName(WScript.ScriptFullName)

' Constrói o caminho para run.bat
strBatchFile = strScriptDir & "\run.bat"

' Verifica se o arquivo existe
If Not objFSO.FileExists(strBatchFile) Then
    MsgBox "Erro: Arquivo run.bat não encontrado em " & strScriptDir, vbCritical, "Erro"
    WScript.Quit 1
End If

' Executa o batch sem mostrar a janela (0 = hide window)
' Usa cmd /c para executar o batch
strCmd = "cmd /c """ & strBatchFile & """"
objShell.Run strCmd, 0, False
