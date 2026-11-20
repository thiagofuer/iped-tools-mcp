# Solução de Problemas e Diagnóstico (FAQ)

Se você encontrar dificuldades na configuração ou execução do **IPED Tools MCP**, consulte este guia prático com as soluções para os problemas mais frequentes.

---

## ❓ 1. O servidor MCP não inicia ou o cliente de IA dá erro de conexão

### Causa Comum A: Caminho do executável com barras invertidas não escapadas
Em arquivos JSON (como `claude_desktop_config.json` ou `mcp.json`), a barra invertida do Windows (`\`) é um caractere de escape reservado.
* ❌ **Incorreto:** `"C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe"`
* ✅ **Correto:** `"C:\\Program Files\\IPED Tools MCP\\IPED-Tools-MCP.exe"`

> 💡 **Dica:** Utilize o botão **"Copiar Configuração"** no [Configurador Gráfico](Configurador-Grafico). Ele já gera o caminho com o escape duplo exato automaticamente.

### Causa Comum B: Processo travado em segundo plano
Se uma sessão anterior do executável não foi finalizada pelo cliente de IA, abra o Gerenciador de Tarefas do Windows (`Ctrl+Shift+Esc`), procure por `IPED-Tools-MCP.exe` em *Detalhes* e finalize o processo.

> ℹ️ **Várias instâncias são normais:** cada cliente de IA ou sessão ativa (por exemplo, o chat do Claude Desktop e cada sessão aberta da aba Code) inicia a sua própria instância do servidor, porque a comunicação por STDIO não pode ser compartilhada. Cada instância aparece como dois processos `IPED-Tools-MCP.exe`. Uma instância que ainda não recebeu nenhuma chamada não abre o caso e ocupa pouca memória; o caso só é carregado na primeira chamada de ferramenta.

---

## ❓ 2. Nenhuma ferramenta aparece listada no chat do cliente de IA

### Causa Comum A: Falta do argumento obrigatório `--stdio`
O executável precisa do argumento `--stdio` para operar como servidor MCP. Sem esse argumento, ele tentará abrir a interface gráfica em vez de escutar o cliente de IA.
* Certifique-se de que no bloco de argumentos esteja presente:
  ```json
  "args": [
    "--stdio"
  ]
  ```

### Causa Comum B: Diagnóstico rápido com a flag `--version`
Abra o Prompt de Comando (`cmd.exe`) ou PowerShell e teste a execução direta do binário:
```cmd
"C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe" --version
```
Se o comando imprimir os dados da versão e sair com código 0, o executável e seu runtime Java embutido estão íntegros e o problema está restrito à configuração do arquivo JSON do cliente.

---

## ❓ 3. Mensagem: "Nenhum caso aberto nesta sessão" ou `case_open: false`

### O que acontece:
Isso é o esperado no início de cada conversa. Cada conversa tem o seu próprio caso e começa **sem caso aberto**. O caso não vem da configuração do cliente nem do configurador gráfico.

### Como resolver:
Peça diretamente para o modelo:
> *"Abra o caso pericial localizado em C:\casos_forenses\caso_operacao_01"*

A IA chamará a ferramenta `open_case`, que abre o caso só nesta conversa. O prompt `start_case` também orienta o modelo a pedir a pasta do caso quando nenhum estiver aberto.

### Observações:
* **`--case` na configuração:** versões anteriores aceitavam o argumento `--case`. Ele não é mais usado: o servidor o ignora, registra no log o aviso *"O argumento '--case' não é mais suportado e foi ignorado"* e inicia sem caso. Remova-o da configuração do cliente.
* **Arquivo `active_case.txt`:** versões anteriores compartilhavam o caso pelo arquivo `%USERPROFILE%\.iped-tools-mcp\active_case.txt`. Ele agora é ignorado e pode ser apagado.
* **Testar a instalação:** no [Configurador Gráfico](Configurador-Grafico), use **Testar o MCP** com a pasta do caso. Se o teste abre o caso e a conversa não, compare o caminho informado ao modelo com o usado no teste.

---

## ❓ 4. Onde consultar os logs de erro e diagnósticos?

O IPED Tools MCP grava o próprio log em arquivo, nos dois modos de execução, numa pasta fixa:

```text
%USERPROFILE%\.iped-tools-mcp\logs\
```

| Arquivo | O que registra |
|---|---|
| `iped-tools-mcp-stdio.log` | O servidor iniciado pelo cliente de IA (`--stdio`): Claude Desktop, Claude Code, LM Studio, Cursor etc. |
| `iped-tools-mcp-gui.log` | O configurador gráfico, incluindo tudo o que aparece na área de log da janela. |
| `hs_err_pid<número>.log` | Relatório de queda da JVM. Quando o processo morre de forma abrupta, a JVM grava esse relatório na pasta temporária, e o MCP o copia para esta pasta na inicialização seguinte. |

* **Rotação:** cada arquivo vai até 5 MB, e são guardados os 5 anteriores (`.1` a `.5`).
* **Várias instâncias:** cada cliente de IA ou sessão ativa tem a sua instância, e todas gravam no mesmo arquivo. Cada linha traz o número do processo (`[pid 12345]`), para separar uma instância da outra.
* **Cabeçalho de diagnóstico:** cada início registra um bloco entre `===== Diagnóstico de inicialização =====` e `===== Fim do diagnóstico =====`, com:
  - versões do MCP, do IPED e do Java;
  - modo e argumentos;
  - diretório de trabalho e codificações;
  - `iped.root`;
  - as pastas do PATH ligadas a Python, JEP ou IPED, com as DLLs encontradas nelas.
* **Etapas da abertura do caso:** cada etapa (configurações do IPED, tipos de metadados, índice e marcadores) registra `[abertura do caso] início:` e `fim:`, com a duração. **Se a última linha do log for um `início:` sem o `fim:` correspondente, o processo morreu dentro daquela etapa.** Nesse caso, procure também um `hs_err_pid*.log` na pasta.
* **Pelo chat:** quando o caso não abre, a ferramenta `get_server_status` traz `last_error` (etapa, mensagem e horário) e `log_file` (o caminho do log). A IA pode informar isso diretamente.

**O que o log contém e o que não contém:**
* **Contém:** caminhos de casos, o nome do usuário do Windows (nos caminhos e no PATH), versões, IDs de itens, nomes de ferramentas, durações e mensagens de erro.
* **Não contém:** conteúdo de itens do caso (texto, metadados das evidências), texto de consultas nem valores devolvidos pelas ferramentas.
* **Fica só na sua máquina.** Nada é enviado automaticamente. Revise os arquivos antes de compartilhar.

**Para pedir suporte**, envie a pasta `logs` inteira, ou pelo menos:
1. o `iped-tools-mcp-stdio.log` (problema no chat) ou o `iped-tools-mcp-gui.log` (problema no configurador);
2. todos os `hs_err_pid*.log` da pasta, se houver;
3. o log do cliente de IA, se for problema de conexão:
   * **No Claude Desktop:** `%APPDATA%\Claude\logs\mcp*.log`;
   * **No LM Studio:** aba **MCP** → servidor `iped` → painel **Server Logs**.

Por definição do protocolo STDIO MCP, a saída padrão (`stdout`) é reservada para os pacotes JSON-RPC. Por isso, além do arquivo, o log também vai para a saída de erros (`stderr`), que o cliente de IA captura no log dele.

---

## ❓ 5. Erro ao abrir caso: "Índice Lucene não encontrado"

O IPED Tools MCP exige que a pasta selecionada seja a raiz de um caso já processado pelo IPED.
* Certifique-se de que dentro da pasta selecionada exista o diretório `iped\index\`.
* Caso o processamento do IPED ainda esteja em andamento no IPED Desktop/CLI, aguarde a conclusão da indexação antes de abrir o caso no MCP.

---

## ❓ 6. Alertas no log: "JEP not found" e `UnsatisfiedLinkError` do JEP

Ao inspecionar o log, você verá mensagens como:
```text
INFO  [br.co.ip.mc.se.PythonRuntimeGuard] Python embutido do IPED desativado: o MCP não executa tarefas de processamento. ...
java.lang.UnsatisfiedLinkError: Can't load library: ...\iped-tools-mcp-python-disabled\jep.dll
ERROR [ip.pa.py.PythonParser] JEP não encontrado, todos os módulos python foram desligados.
```

* **Essas mensagens são esperadas:**
  1. O MCP só lê casos já processados e não precisa de Python. Por isso, antes de carregar as configurações do IPED, ele desativa o Python embutido do IPED (JEP), apontando a biblioteca para um arquivo que não existe.
  2. O IPED registra a falha do JEP, desliga os módulos em Python e segue normalmente com os parsers nativos, Lucene, Tika e Sleuthkit.
  3. Logo depois, o log mostra `[abertura do caso] fim: configurações do IPED` e `Caso carregado com sucesso`, confirmando que o servidor está pronto.
* **Python instalado na máquina não afeta o MCP:** instalações de Python ou do IPED no PATH, `PYTHONHOME` ou `PYTHONPATH` não são usadas. Os resultados que o IPED gravou no caso, como faces, similaridade de imagens, transcrições, OCR e detecções de IA, continuam disponíveis.
* **Versões anteriores:** sem essa desativação, um Python de outra versão no ambiente podia travar a abertura do caso. O log parava em `[abertura do caso] início: configurações do IPED`, sem o `fim:`, e o cliente acabava fechando a conexão. Se você vê esse sintoma, atualize o IPED Tools MCP. Se ele persistir, envie o log e os `hs_err_pid*.log` ao suporte.
* As antigas mensagens `main ERROR Unrecognized format specifier [d]` não aparecem mais a partir desta versão.

---

## ❓ 7. "Server transport closed unexpectedly" no Claude Desktop

Se o Claude Desktop fechar o canal de comunicação logo na abertura:
1. **Verifique se o Claude Desktop foi completamente reiniciado:** Feche o Claude Desktop e abra-o novamente após salvar o arquivo `claude_desktop_config.json`.
2. **Tempo de carregamento de casos gigantescos:** o caso não é aberto ao iniciar o servidor, e sim quando o modelo chama `open_case`, que por isso leva alguns segundos a mais. Se o caso possuir centenas de milhares de documentos (ex: 700.000+ arquivos), o IPED Core pode levar 2 a 3 segundos para ler o índice Lucene do disco. A conexão inicial (`initialize` e `tools/list`) não depende do caso. Se o `open_case` estourar o tempo limite do cliente, repita-o: a leitura seguinte aproveita o cache de disco do sistema operacional.
3. **Caminho do executável:** Certifique-se de apontar para a versão de produção instalada (`C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe`) ou para o executável gerado na pasta portátil.


---

## ❓ 8. O prompt personalizado não está sendo usado

### O que acontece:
Você editou a metodologia do `start_case` (`%USERPROFILE%\.iped-tools-mcp\prompts\start_case.md`), mas a IA continua recebendo o texto padrão.

### Como verificar:
1. **No [Configurador Gráfico](Configurador-Grafico):** a linha de situação da seção **Prompt inicial da investigação** diz *"Metodologia personalizada inválida"* e o motivo: arquivo vazio, com mais de 200 KB, fora do UTF-8 ou ilegível. Enquanto isso, vale a metodologia padrão.
2. **No log do servidor** (`iped-tools-mcp-stdio.log`): a cada `start_case` com cópia inválida aparece *"Prompt start_case personalizado ignorado"*, com o caminho e o motivo. Uma cópia desatualizada continua valendo e aparece uma vez por processo como *"Prompt start_case personalizado desatualizado"*.

### Como resolver:
* **Codificação:** salve o arquivo em **UTF-8** (no Bloco de Notas: *Salvar como* → *Codificação: UTF-8*).
* **Arquivo vazio ou grande demais:** corrija o texto, ou use **Restaurar padrão** e recomece pelo **Editar**.
* **O prompt é lido a cada `start_case`:** não é preciso reiniciar o cliente, mas é preciso acionar o `start_case` de novo numa conversa. Uma conversa já iniciada guarda o texto que recebeu.
* **Clientes sem menu de prompts:** o texto colado vem do botão **Copiar prompt**; copie de novo depois de editar.
