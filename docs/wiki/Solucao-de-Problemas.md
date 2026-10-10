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

## ❓ 3. Mensagem: "Nenhum caso ativo" ou `case_open: false`

### O que acontece:
O servidor MCP foi iniciado via STDIO, mas ainda não há nenhum caminho gravado em `%USERPROFILE%\.iped-tools-mcp\active_case.txt` e o parâmetro `--case` não foi utilizado.

### Como resolver:
Você tem duas opções simples e imediatas:
1. **Pela Interface Gráfica:** Dê um duplo-clique em `IPED-Tools-MCP.exe`, selecione o caso no botão *"Selecionar Caso IPED..."*. O caso ativo é sincronizado instantaneamente com o chat.
2. **Pelo Chat com a IA:** Peça diretamente para o modelo:
   > *"Abra o caso pericial localizado em C:\casos_forenses\caso_operacao_01"*
   A IA chamará a ferramenta `open_case`, carregando os índices do IPED na memória em tempo de execução.

---

## ❓ 4. Onde consultar os logs de erro e diagnósticos?

Por definição do protocolo STDIO MCP, a saída padrão (`stdout`) é reservada com exclusividade para os pacotes de comunicação JSON-RPC.

**Todos os logs do sistema, avisos do Lucene, mensagens do Apache Tika e erros do Quarkus são direcionados para a saída de erros (`stderr`).**
* **No Claude Desktop:** Os logs são salvos em:
  ```text
  %APPDATA%\Claude\logs\mcp*.log
  ```
* **No LM Studio:** Acesse a aba **MCP**, clique no servidor `iped` e visualize o painel de **Server Logs**.

---

## ❓ 5. Erro ao abrir caso: "Índice Lucene não encontrado"

O IPED Tools MCP exige que a pasta selecionada seja a raiz de um caso já processado pelo IPED.
* Certifique-se de que dentro da pasta selecionada exista o diretório `iped\index\`.
* Caso o processamento do IPED ainda esteja em andamento no IPED Desktop/CLI, aguarde a conclusão da indexação antes de abrir o caso no MCP.

---

## ❓ 6. Alertas no log: "no jep in java.library.path" ou "Unrecognized format specifier"

Ao inspecionar o log do Claude Desktop ou LM Studio, você poderá ver mensagens como:
```text
main ERROR Unrecognized format specifier [d]
java.lang.UnsatisfiedLinkError: no jep in java.library.path
ERROR [ip.pa.py.PythonParser] JEP não encontrado, todos os módulos python foram desligados.
```

* **Essas mensagens são completamente normais e inofensivas:**
  1. O protocolo MCP STDIO envia toda a saída de log interno para o canal de erros (`stderr`) para não poluir o canal de dados (`stdout`). O cliente de IA captura o `stderr` e registra no log.
  2. O IPED Core tenta carregar a biblioteca nativa JEP (Java Embedded Python). Se o ambiente não possuir o JEP configurado no Windows, o IPED simplesmente desliga os módulos opcionais em Python e segue funcionando normalmente com todos os parsers nativos, Lucene, Tika e Sleuthkit.
  3. Logo após essa mensagem, você verá no log: `"Caso carregado com sucesso"`, confirmando que o servidor está pronto para responder.

---

## ❓ 7. "Server transport closed unexpectedly" no Claude Desktop

Se o Claude Desktop fechar o canal de comunicação logo na abertura:
1. **Verifique se o Claude Desktop foi completamente reiniciado:** Feche o Claude Desktop e abra-o novamente após salvar o arquivo `claude_desktop_config.json`.
2. **Tempo de carregamento de casos gigantescos:** Se o caso ativo possuir centenas de milhares de documentos (ex: 700.000+ arquivos), o IPED Core pode levar 2 a 3 segundos para ler todo o índice Lucene do disco na primeira vez. Se o Claude disparar timeout antes de receber a resposta, certifique-se de que o caso já foi aberto previamente pela interface gráfica do IPED Tools MCP (o que agiliza a leitura via cache de disco do sistema operacional).
3. **Caminho do executável:** Certifique-se de apontar para a versão de produção instalada (`C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe`) ou para o executável gerado na pasta portátil.

