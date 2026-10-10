# Configurador Gráfico e Sincronização de Caso

O executável `IPED-Tools-MCP.exe` opera em modo duplo (*dual-mode*). Quando iniciado sem argumentos de linha de comando ou via duplo-clique no atalho da Área de Trabalho/Menu Iniciar, ele abre o **Configurador Gráfico (GUI)** em Java Swing.

---

## 🖥️ Visão Geral da Interface Gráfica

A interface gráfica foi concebida para simplificar a vida do perito ou analista, eliminando a necessidade de editar manualmente arquivos de configuração JSON complexos:

1. **Seletor de Caso Pericial:**
   * Clique em **"Selecionar Caso IPED..."** para navegar até a pasta raiz de um caso pericial processado.
   * O sistema valida imediatamente a presença dos índices em `iped/index/`.
2. **Painel de Métricas e Estatísticas:**
   * Exibe o resumo quantitativo do caso carregado: total de itens indexados, categorias forenses identificadas, marcadores existentes e fontes de evidência (discos, imagens periciais, dispositivos móveis).
3. **Gerador de Configuração 1-Click:**
   * Permite selecionar o cliente de IA desejado e copiar a configuração pronta diretamente para a área de transferência.

---

## 🔄 Perfis de Aplicativos de IA Disponíveis

No menu suspenso *"Selecione seu aplicativo de IA:"*, você encontra três opções correspondentes aos principais fluxos de trabalho:

1. **`LM Studio (Formulário Visual - Copiar Campos)`:**
   * Instruções passo a passo com os campos exatos (Nome, Comando e Argumento) para preenchimento na interface visual do LM Studio.
2. **`LM Studio (Arquivo ng-mcp.json)`:**
   * Bloco JSON pronto para inserção no arquivo de configuração do LM Studio.
3. **`Claude Desktop / Cursor / Antigravity (JSON)`:**
   * Bloco JSON padrão da especificação MCP (`mcpServers`) compatível com Claude Desktop, Cursor e Antigravity.

---

## 📌 A Opção "Fixar caso no comando (--case)"

Ao lado do seletor de aplicativo, há a caixa de seleção:

> `[ ] Fixar caso no comando (--case)`

* **Desmarcado (Recomendado):**
  O comando do servidor MCP é configurado com apenas o argumento `--stdio`. Dessa forma, o servidor lê dinamicamente o caso ativo compartilhado. **Você só precisa configurar seu cliente de IA uma única vez!** Sempre que você trocar de caso na interface gráfica ou pedir para a IA abrir um novo caso no chat (`open_case`), o servidor sincroniza automaticamente.
* **Marcado (Opcional / Caso Fixo):**
  O caminho completo do caso é embutido nos argumentos (`--stdio --case "C:\casos_forenses\caso_operacao_01"`). O servidor sempre iniciará apontando para aquele caso específico.

---

## ⚡ Sincronização Bidirecional do Caso Ativo

A sincronização entre o chat do modelo de IA e a interface gráfica ocorre através do arquivo de estado pericial do usuário:

```text
%USERPROFILE%\.iped-tools-mcp\active_case.txt
```

### Como a sincronização funciona:
1. **Da GUI para a IA:** Ao abrir um caso na interface gráfica, o caminho é gravado imediatamente em `active_case.txt`. As próximas consultas feitas pela IA no cliente MCP já operam sobre esse caso, sem necessidade de reiniciar o cliente de IA.
2. **Da IA para a GUI:** Quando a IA executa a ferramenta forense `open_case(path="C:\casos_forenses\outro_caso")` durante a conversa, o arquivo `active_case.txt` é atualizado. A interface gráfica possui um monitor periódico (a cada 1,5 segundo) que detecta a alteração e atualiza automaticamente as métricas e o caso exibido na janela.
