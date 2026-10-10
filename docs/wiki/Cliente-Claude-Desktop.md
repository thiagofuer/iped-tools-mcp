# Configuração no Claude Desktop

O [Claude Desktop](https://claude.ai/download) suporta nativamente o Model Context Protocol (MCP), permitindo interagir com casos periciais do IPED diretamente na interface de chat da Anthropic.

---

## 📍 Localização do Arquivo de Configuração

O Claude Desktop lê suas integrações MCP do arquivo de configuração JSON do usuário. A forma mais rápida e universal de abri-lo (independente da versão instalada) é pelo próprio Claude:

> 💡 **Forma Universal Recomendada:**
> No Claude Desktop, acesse o menu (ou ícone de configurações) em **Settings > Developer > Edit Config**. O arquivo será aberto diretamente no seu editor de texto padrão.

Caso queira localizá-lo manualmente no Explorador de Arquivos do Windows:

* **Instalador Tradicional (.exe / Win32):**
  ```text
  %APPDATA%\Claude\claude_desktop_config.json
  ```
  *(Equivale a: `C:\Users\<SeuUsuario>\AppData\Roaming\Claude\claude_desktop_config.json`)*

* **Instalador da Microsoft Store (.msix / AppX):**
  Quando instalado pela Windows Store, o Windows isola a pasta de dados do usuário:
  ```text
  %LOCALAPPDATA%\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude\claude_desktop_config.json
  ```

Se o arquivo ainda não existir, você pode criá-lo ou utilizar o botão **Edit Config** do Claude Desktop para criá-lo automaticamente.

---

## 📝 Bloco de Configuração (Recomendado — Caso Dinâmico)

Abra o arquivo `claude_desktop_config.json` e insira o bloco abaixo dentro da chave `"mcpServers"` (gerado com 1-click no [Configurador Gráfico](Configurador-Grafico)):

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

*(Se estiver utilizando a versão portátil, substitua pelo caminho correspondente, por exemplo: `"C:\\Ferramentas\\IPED-Tools-MCP\\IPED-Tools-MCP.exe"`)*

> 💡 **Nota Importante:** Note que **não é necessário fixar o parâmetro `--case`**! Com apenas `--stdio`, o servidor MCP sincroniza automaticamente com o caso selecionado na interface gráfica do IPED Tools MCP ou com o caso que você solicitar para a IA abrir no chat através da ferramenta `open_case`.

---

## 📌 Configuração com Caso Fixo (Opcional)

Se você preferir travar a inicialização sempre em um caso pericial específico:

```json
{
  "mcpServers": {
    "iped": {
      "command": "C:\\Program Files\\IPED Tools MCP\\IPED-Tools-MCP.exe",
      "args": [
        "--stdio",
        "--case",
        "C:\\casos_forenses\\caso_operacao_01"
      ]
    }
  }
}
```

---

## 🚀 Como Validar e Iniciar a Investigação

1. Salve o arquivo `claude_desktop_config.json`.
2. Reinicie o Claude Desktop completamente (fechando e reabrindo o aplicativo).
3. No canto inferior da barra de digitação do Claude, você verá o ícone de ferramentas MCP indicando que o servidor `iped` está ativo com as 27 ferramentas periciais disponíveis.
4. Digite a saudação pericial ou chame o prompt `/start_case` para iniciar a análise probatória com rigor metodológico.
