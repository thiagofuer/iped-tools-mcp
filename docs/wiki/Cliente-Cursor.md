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
   * **Command:** `C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe --stdio`

### Método 2: Arquivo JSON de Configuração (`mcp.json`)
Você também pode configurar por projeto criando o arquivo `.cursor/mcp.json` na raiz da sua pasta de trabalho, ou no arquivo de configuração global do Cursor:

```json
{
  "mcpServers": {
    "iped": {
      "command": "C:\\Program Files\\IPED Tools MCP\\IPED-Tools-MCP.exe",
      "args": [
        "--stdio"
      ]
    }
  }
}
```

---

## 💡 Sincronização do Caso

Assim como nos demais clientes, você **não precisa fixar `--case`**. O Cursor se conectará ao caso atualmente selecionado no [Configurador Gráfico](Configurador-Grafico) ou ao caso ativo definido no arquivo compartilhado `%USERPROFILE%\.iped-tools-mcp\active_case.txt`.

Para alternar de caso durante uma sessão, basta selecionar um novo caso na janela gráfica do IPED Tools MCP ou pedir no chat do Cursor:
> *"Abra o caso pericial localizado em C:\casos_forenses\outro_caso"*

A ferramenta `open_case` será invocada e os índices serão alternados em memória instantaneamente.
