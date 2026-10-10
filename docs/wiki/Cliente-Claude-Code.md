# Configuração no Claude Code

O [Claude Code](https://docs.anthropic.com/en/docs/agents-and-tools/claude-code/overview) é a ferramenta oficial de linha de comando da Anthropic para agentes autônomos. Ele permite executar investigações forenses interativas diretamente no terminal através de comandos estruturados.

---

## ⚡ Registro do Servidor MCP

Para adicionar o IPED Tools MCP ao Claude Code, abra o PowerShell ou terminal e execute o comando `claude mcp add`:

```powershell
claude mcp add iped "C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe" --stdio
```

*(Caso utilize o pacote portátil, forneça o caminho do executável extraído, por exemplo: `claude mcp add iped "C:\Ferramentas\IPED-Tools-MCP\IPED-Tools-MCP.exe" --stdio`)*

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

O Claude Code carregará automaticamente as ferramentas MCP disponíveis. Para orientar o agente no contexto forense do caso aberto, você pode digitar:

```text
/mcp prompt start_case
```

Ou simplesmente perguntar diretamente:
> *"Qual é o caso atualmente carregado no IPED e quantas evidências foram indexadas?"*

O Claude Code acionará as ferramentas `get_server_status` ou `get_case_summary` para responder de forma fundamentada e auditável.
