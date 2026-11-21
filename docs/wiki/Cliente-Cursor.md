# Configuração no Cursor

O [Cursor](https://www.cursor.com/) suporta servidores MCP, permitindo que desenvolvedores, peritos e pesquisadores consultem casos periciais do IPED dentro de seu ambiente de análise de código e relatórios técnicos.

---

## 📍 Onde Configurar no Cursor

Você pode configurar o servidor MCP no Cursor de duas formas:

### Método 1: Interface Gráfica de Configurações
1. No Cursor, abra as configurações (**Settings** ou `Ctrl+,`).
2. Navegue até a seção **Features > MCP Servers**.
3. Clique em **Add New MCP Server**.
4. Defina:
   * **Name:** `iped`
   * **Type:** `command`
   * **Command:** `C:\Program Files\IPED-Tools-MCP\IPED-Tools-MCP.exe --stdio`

### Método 2: Arquivo JSON de Configuração (`mcp.json`)
Você também pode configurar por projeto criando o arquivo `.cursor/mcp.json` na raiz da sua pasta de trabalho, ou no arquivo de configuração global do Cursor:

```json
{
  "mcpServers": {
    "iped": {
      "command": "C:\\Program Files\\IPED-Tools-MCP\\IPED-Tools-MCP.exe",
      "args": [
        "--stdio"
      ]
    }
  }
}
```

---

## 💡 Caso por Conversa

A configuração tem só `--stdio`, sem caso. Cada conversa começa **sem caso aberto**. Para abrir ou trocar o caso, peça no chat do Cursor:
> *"Abra o caso pericial localizado em C:\casos_forenses\outro_caso"*

A ferramenta `open_case` será invocada e o caso será aberto só nesta conversa. O caso de uma conversa não muda o de outra, e o [Configurador Gráfico](Configurador-Grafico) não muda o caso de nenhuma conversa.

O argumento `--case` de versões anteriores não é mais usado: se ainda estiver na configuração, o servidor o ignora e inicia sem caso.
