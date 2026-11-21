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

## 📝 Bloco de Configuração (Recomendado)

Abra o arquivo `claude_desktop_config.json` e insira o bloco abaixo dentro da chave `"mcpServers"` (gerado com 1-click no [Configurador Gráfico](Configurador-Grafico)):

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

*(Se estiver utilizando a versão portátil, substitua pelo caminho correspondente, por exemplo: `"C:\\Ferramentas\\IPED-Tools-MCP\\IPED-Tools-MCP.exe"`)*

> 💡 **Nota Importante:** a configuração tem só `--stdio`, sem caso. Cada conversa começa sem caso aberto: peça ao Claude para abrir a pasta do caso (por exemplo, *"abra o caso C:\casos_forenses\caso_operacao_01"*), e ele chama a ferramenta `open_case`. O caso de uma conversa não muda o de outra.
>
> O argumento `--case` de versões anteriores não é mais usado. Se ele estiver na sua configuração, o servidor o ignora e inicia sem caso; você pode removê-lo.

---

## 🚀 Como Validar e Iniciar a Investigação

1. Salve o arquivo `claude_desktop_config.json`.
2. Reinicie o Claude Desktop completamente (fechando e reabrindo o aplicativo).
3. No canto inferior da barra de digitação do Claude, você verá o ícone de ferramentas MCP indicando que o servidor `iped` está ativo com as 30 ferramentas periciais disponíveis.
4. Para iniciar a análise com a metodologia pericial, abra o menu **+** da caixa de mensagem, escolha o servidor `iped` e selecione o prompt `start_case`. Preencha os parâmetros opcionais (`case_path`, `investigation_target`) e envie. O texto do prompt aparece anexado à mensagem antes do envio.

> ⚠️ Digitar `start_case` como texto comum não aciona o prompt: a mensagem chega à IA como um pedido qualquer, sem a metodologia e sem as regras de conduta. Veja [Fluxo de Investigação](Fluxo-de-Investigacao).
