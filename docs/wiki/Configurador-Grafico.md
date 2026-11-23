# Configurador Gráfico e Teste Local do MCP

O executável `IPED-Tools-MCP.exe` opera em modo duplo (*dual-mode*). Quando iniciado sem argumentos de linha de comando ou via duplo-clique no atalho da Área de Trabalho/Menu Iniciar, ele abre o **Configurador Gráfico (GUI)** em Java Swing.

O configurador serve para três coisas:

1. **Testar o MCP** do mesmo jeito que o cliente de IA vai executá-lo.
2. **Copiar e personalizar o prompt inicial** da investigação (`start_case`).
3. **Gerar a configuração** do cliente de IA.

O configurador **não abre nem define o caso das conversas**. Cada conversa com o modelo de IA tem o seu próprio caso, aberto com a ferramenta `open_case` (veja [Caso por conversa](#-caso-por-conversa)).

---

## 🧪 1. Testar o MCP (modo STDIO)

O teste executa o servidor com o mesmo comando e os mesmos argumentos da configuração gerada para o cliente de IA (`--stdio`). Ele roda num processo separado e conversa com o servidor pelo protocolo MCP, como o cliente faria. Assim ele confere tudo o que o cliente vai usar: o caminho do executável, as opções da JVM, a codificação UTF-8 e a saída padrão reservada ao protocolo.

### Como executar

1. **Sem caso:** deixe o campo *"Pasta do caso (opcional)"* vazio e clique em **▶ Executar teste**. O teste confere a inicialização, a lista de ferramentas e que a sessão começa sem caso aberto.
2. **Com um caso:** clique em **Procurar Pasta...**, escolha a pasta raiz de um caso processado pelo IPED e clique em **▶ Executar teste**. Além das etapas acima, o teste abre o caso com `open_case` e lê o resumo com `get_case_summary`.

O caso escolhido é aberto **só no processo do teste**. Ele não muda o caso de nenhuma conversa em andamento, e a pasta não é gravada em lugar nenhum: ao fechar a janela, ela é esquecida.

O botão **Cancelar** encerra o teste a qualquer momento. Fechar a janela durante um teste também encerra o processo do teste.

### Como ler o resultado

Cada etapa aparece com ✔ (passou), ❌ (falhou) ou – (não executada, porque uma etapa anterior falhou), com a duração:

| Etapa | O que confere |
|---|---|
| Iniciar o servidor | O executável (ou o `java -jar`) da configuração existe e inicia. |
| `initialize` | O servidor responde ao protocolo e informa a **mesma versão** da janela. |
| `tools/list` | As ferramentas estão disponíveis, inclusive `open_case`. |
| `get_server_status` | A sessão começa **sem caso aberto**. |
| `open_case` | O caso escolhido abre (só com pasta escolhida). Casos grandes podem levar alguns minutos. |
| `get_case_summary` | O total de itens, categorias e marcadores do caso. |

### O que fazer quando uma etapa falha

* **Iniciar o servidor:** confira se o executável indicado na configuração existe. Se a janela foi aberta fora do pacote (pela IDE, por exemplo), o teste avisa que precisa da aplicação empacotada.
* **`initialize` com versão diferente:** o cliente de IA executaria outra instalação. Reinstale ou gere a configuração novamente por esta janela.
* **"escreveu na saída padrão algo que não é uma mensagem JSON-RPC":** a linha mostrada impede o cliente de IA de funcionar. Envie a linha e o log ao suporte.
* **"Sem resposta" ou "terminou antes de responder":** o console de diagnóstico mostra as últimas linhas do log do servidor e o caminho do arquivo de log. Veja também a [Solução de Problemas](Solucao-de-Problemas).
* **`open_case` com erro:** a mensagem explica o motivo (pasta que não é de caso, índice danificado etc.).

---

## 📝 2. Prompt inicial da investigação (`start_case`)

O `start_case` traz a metodologia de investigação e as regras de conduta (veja [Como Investigar com o MCP](Como-Investigar-com-o-MCP)). Esta seção mostra qual metodologia está em uso e permite copiar o prompt ou personalizá-lo.

### Situação
A linha de situação mostra uma de quatro mensagens:

| Situação | Significado |
|---|---|
| Metodologia padrão da versão | Não há cópia personalizada: vale a metodologia da versão instalada. |
| Metodologia personalizada | Vale a sua cópia em `%USERPROFILE%\.iped-tools-mcp\prompts\start_case.md`. |
| Personalizada desatualizada | A sua cópia foi criada a partir do padrão de uma versão anterior, e o padrão mudou. Ela continua valendo, mas compare-a com o padrão. |
| Personalizada inválida | A cópia está vazia, tem mais de 200 KB, não está em UTF-8 ou não pôde ser lida. Vale a metodologia padrão até a cópia ser corrigida. |

A situação é atualizada quando a janela volta ao primeiro plano, por exemplo depois de você salvar o arquivo no editor.

### Botões
* **📋 Copiar prompt:** copia o prompt completo, com os campos opcionais **Foco** e **Pasta do caso**. É o mesmo texto que o servidor entrega no `start_case`. Use em clientes que não oferecem os prompts do MCP: cole o texto como **primeira mensagem** da conversa.
* **Editar:** na primeira vez, cria a cópia personalizada a partir do padrão da versão instalada; depois, abre a cópia existente sem alterá-la. O arquivo abre no editor associado a `.md`. Se não houver um, a janela abre a pasta e mostra o caminho do arquivo. A edição vale no próximo `start_case`, sem reiniciar o cliente de IA.
* **Abrir pasta:** abre `%USERPROFILE%\.iped-tools-mcp\prompts`.
* **Restaurar padrão:** depois de confirmar, renomeia a cópia para `start_case.md.bak-<data e hora>`, na mesma pasta, e volta à metodologia padrão.

### O que pode e o que não pode ser editado
* **Pode:** a persona, o idioma e as etapas da metodologia.
* **Não pode:** as **regras de conduta** (acesso só pelas ferramentas do MCP, checagem de conteúdo sensível, consentimento do modelo em nuvem, aceite por sessão e confirmação de escritas). Elas não estão no arquivo: o aplicativo as acrescenta sempre ao fim do prompt, e elas prevalecem sobre o restante do texto.
* A primeira linha do arquivo (`<!-- iped-tools-mcp start_case base: ... -->`) registra de qual padrão a cópia foi criada e não vai para o modelo. Se você apagá-la, a cópia continua valendo, mas o configurador não consegue avisar quando ela ficar desatualizada.

---

## 🔄 3. Configuração do Cliente de IA

No menu suspenso *"Selecione seu aplicativo de IA:"*, você encontra três opções correspondentes aos principais fluxos de trabalho:

1. **`LM Studio (Formulário Visual - Copiar Campos)`:**
   * Instruções passo a passo com os campos exatos (Nome, Comando e Argumento) para preenchimento na interface visual do LM Studio. Os mesmos campos servem para o [Codex (app da OpenAI)](Cliente-Codex), em Plugins → MCPs.
2. **`LM Studio (Arquivo ng-mcp.json)`:**
   * Bloco JSON pronto para inserção no arquivo de configuração do LM Studio.
3. **`Claude Desktop / Cursor / Antigravity (JSON)`:**
   * Bloco JSON padrão da especificação MCP (`mcpServers`) compatível com Claude Desktop, Cursor e Antigravity.

A configuração tem apenas o argumento `--stdio`, e **não inclui caso**. Você configura o cliente de IA uma única vez, e o caso é escolhido em cada conversa.

---

## 📂 Caso por conversa

* **Cada conversa tem o seu próprio caso.** Uma conversa nova começa **sem caso aberto**.
* **Para abrir ou trocar o caso**, peça ao modelo na conversa, por exemplo: *"abra o caso D:\casos\operacao_01"*. O modelo chama a ferramenta `open_case`. O prompt `start_case` também orienta o modelo a pedir a pasta do caso quando nenhum estiver aberto.
* **Um caso aberto numa conversa não muda o caso de outra**, nem de outro aplicativo de IA conectado ao mesmo tempo. O configurador também não muda o caso de nenhuma conversa.
* O caso aberto numa conversa não é gravado no perfil do usuário. Versões anteriores usavam o arquivo `%USERPROFILE%\.iped-tools-mcp\active_case.txt`, que agora é ignorado e pode ser apagado.
* O argumento `--case` não é mais usado. Se ele ainda estiver na configuração do seu cliente de IA, o servidor o ignora, registra um aviso no log e inicia sem caso. Você pode removê-lo da configuração.
