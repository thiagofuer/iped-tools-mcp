# Configuração no Claude Code

O [Claude Code](https://docs.anthropic.com/en/docs/agents-and-tools/claude-code/overview) é a ferramenta oficial de linha de comando da Anthropic para agentes autônomos. Ele permite executar investigações forenses interativas diretamente no terminal através de comandos estruturados.

---

## ⚡ Registro do Servidor MCP

Para adicionar o IPED Tools MCP ao Claude Code, abra o PowerShell ou terminal e execute o comando `claude mcp add`:

```powershell
claude mcp add iped -- "C:\Program Files\IPED-Tools-MCP\IPED-Tools-MCP.exe" --stdio
```

O `--` separa as opções do `claude` do comando do servidor: tudo o que vem depois dele, inclusive o `--stdio`, é passado ao executável do IPED Tools MCP. O nome `iped` é o nome do servidor no Claude Code e aparece nos comandos dos prompts (veja abaixo).

*(Caso utilize o pacote portátil, forneça o caminho do executável extraído, por exemplo: `claude mcp add iped -- "C:\Ferramentas\IPED-Tools-MCP\IPED-Tools-MCP.exe" --stdio`)*

---

## 🔍 Verificando o Registro

Para confirmar que o servidor foi registrado corretamente no ambiente do Claude Code, execute:

```powershell
claude mcp list
```

A saída deverá listar o servidor `iped` com status configurado para o comando especificado.

---

## 💬 Utilização no Terminal

Ao iniciar uma sessão de análise no terminal com:

```powershell
claude
```

O Claude Code carregará automaticamente as ferramentas MCP disponíveis. Os prompts MCP aparecem como comandos de barra no formato `/<servidor>:<prompt>`, marcados com **(MCP)** na lista, em que `<servidor>` é o nome usado no `claude mcp add`. Para iniciar a investigação com a metodologia forense e as regras de conduta, acione:

```text
/iped:start_case
```

Digite `/iped` e escolha `/iped:start_case (MCP)` na lista. Se você registrou o servidor com outro nome (por exemplo `iped-dev`), o comando muda junto: `/iped-dev:start_case`. Versões antigas do Claude Code usavam o formato `/mcp__iped__start_case`.

> ⚠️ **Aba Code do app Claude Desktop:** o menu `/` dessa aba não lista os prompts dos servidores MCP. Nela, use o **📋 Copiar prompt** do [Configurador Gráfico](Configurador-Grafico) (seção **Prompt inicial da investigação**) e cole o texto como primeira mensagem da conversa.

Ou simplesmente perguntar diretamente:
> *"Abra o caso em C:\casos_forenses\caso_operacao_01 e me diga quantas evidências foram indexadas."*

O Claude Code chamará `open_case` e `get_case_summary` para responder de forma fundamentada e auditável.

---

## ➡️ Próximo passo

Com o servidor `iped` configurado, siga para **[Como Investigar com o MCP](Como-Investigar-com-o-MCP)**: o passo a passo para iniciar o `start_case`, abrir o caso, responder ao alerta de conteúdo sensível e conduzir a investigação.
