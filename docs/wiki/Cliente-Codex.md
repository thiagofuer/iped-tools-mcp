# Configuração no Codex (app da OpenAI)

O app desktop da OpenAI para Windows tem dois modos: o **chat do ChatGPT** e o **Codex**, o modo de agente. O IPED Tools MCP funciona **só nas conversas do Codex**.

> ⚠️ **O chat comum do ChatGPT não usa o servidor.** Mesmo com o servidor configurado, numa conversa do ChatGPT o modelo responde que a ferramenta não está disponível. Abra uma conversa no **Codex** (menu lateral do app).

> ☁️ **O modelo roda na nuvem da OpenAI.** Ao analisar o caso, texto, metadados e imagens dos itens são enviados ao provedor. As regras de conduta do IPED Tools MCP fazem o modelo contar os itens sinalizados como CSAM e nudez, avisar sobre o envio e **só prosseguir com a sua concordância expressa**. Para casos em que nada pode sair da máquina, use o [LM Studio](Cliente-LM-Studio) com um modelo local.

Testado no app versão 26.1117.

---

## 📋 Método 1: Plugins → MCPs (Recomendado)

1. No app, abra **Configurações → Plugins → MCPs** e adicione um servidor (ou edite o servidor `iped`).
2. Preencha:
   * **Nome:** `iped`
   * **Comando para iniciar:** `C:\Program Files\IPED-Tools-MCP\IPED-Tools-MCP.exe`
   * **Argumentos:** clique em **+ Adicionar argumento** e insira somente:
     ```text
     --stdio
     ```
   * **Variáveis do ambiente** e **Diretório de trabalho:** não são necessários.
3. Salve.

O caminho do executável é o mesmo que o [Configurador Gráfico](Configurador-Grafico) mostra no campo **Comando** da opção *LM Studio (Formulário Visual)*. Se você usa o pacote portátil, informe o caminho da pasta extraída.

---

## 📄 Método 2: Arquivo `config.toml`

O app grava a configuração em `%USERPROFILE%\.codex\config.toml`. O trecho equivalente é:

```toml
[mcp_servers.iped]
enabled = true
command = 'C:\Program Files\IPED-Tools-MCP\IPED-Tools-MCP.exe'
args = ["--stdio"]
```

Use aspas simples no caminho, para a barra invertida não precisar de escape.

---

## 💬 Uso na conversa

1. Abra uma conversa nova no **Codex**.
2. Selecione o servidor na conversa: ele aparece como **IPED-Tools-MCP** na lista de plugins e servidores do app.
3. Para conferir a conexão, peça: *"use o servidor iped e chame get_server_status"*. A resposta deve mostrar o servidor pronto e `case_open: false` (cada conversa começa sem caso aberto).

### Iniciar a investigação com o prompt
O Codex **não lista os prompts MCP**: o menu `/` mostra só os comandos do app, e o **+** adiciona arquivos e plugins. Por isso o `start_case` não pode ser acionado pelo menu. Em vez disso:

1. No [Configurador Gráfico](Configurador-Grafico), seção **Prompt inicial da investigação**, preencha se quiser o foco e a pasta do caso.
2. Clique em **📋 Copiar prompt**.
3. Cole o texto como **primeira mensagem** da conversa do Codex.

O texto traz a metodologia completa e as regras de conduta. Mesmo sem ele, o servidor envia ao Codex um resumo das regras (as *instructions* do protocolo MCP), que chega inteiro ao modelo; o prompt completo, porém, orienta melhor a investigação.

---

## 🩺 Diagnóstico

* **Instâncias que iniciam e param em menos de 1 segundo no log** (`%USERPROFILE%\.iped-tools-mcp\logs\iped-tools-mcp-stdio.log`): o app inicia o servidor só para checar o status, por exemplo ao abrir a tela de MCPs, e o encerra logo depois. Isso é normal e não indica falha.
* **O modelo diz que a ferramenta não existe:** confira se a conversa é do **Codex** e não do chat do ChatGPT, e se o servidor `iped` está selecionado na conversa.
* **Para testar o servidor fora do app**, use **Testar o MCP** no [Configurador Gráfico](Configurador-Grafico).

---

## ➡️ Próximo passo

Com o servidor `iped` configurado, siga para **[Como Investigar com o MCP](Como-Investigar-com-o-MCP)**: o passo a passo para iniciar a investigação, abrir o caso, responder ao alerta de conteúdo sensível e conduzir a análise.
