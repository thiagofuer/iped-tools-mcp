# Configuração no Antigravity

O **Antigravity** integra o Model Context Protocol (MCP), provendo um ambiente avançado de agentes autônomos para perícias forenses complexas, análise automatizada de vestígios e cruzamento de evidências do IPED.

---

## 📍 Configuração do Servidor MCP

No Antigravity, acesse as configurações de servidores MCP através do menu de preferências ou editando diretamente o arquivo de configuração MCP do ambiente (ou no arquivo de workspace correspondente):

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

*(Se estiver utilizando a versão portátil, utilize o caminho correspondente: `"C:\\Ferramentas\\IPED-Tools-MCP\\IPED-Tools-MCP.exe"`)*

---

## 🚀 Iniciar a investigação com o `start_case`

O Antigravity lista os prompts dos servidores MCP no menu de barra. Na caixa de mensagem, digite `/mcp` e escolha:

```text
/mcp:iped:start_case
```

O item aparece com o ícone de balão de mensagem, que distingue os prompts MCP das skills e comandos do próprio Antigravity. Ao selecioná-lo, o cliente busca o prompt no servidor `iped` e coloca na conversa a metodologia de investigação e as regras de conduta. Em seguida, informe a pasta do caso. Se você registrou o servidor com outro nome, o comando muda junto (por exemplo, `/mcp:iped-dev:start_case`).

> ⚠️ Digitar `start_case` como texto comum não aciona o prompt: escolha o item na lista do `/`.

---

## 🛠️ Benefícios no Antigravity

Com o servidor conectado no Antigravity:
* **Automação de Quesitos:** Agentes especializados podem ler volumes maciços de evidências indexadas, checar integridade de hashes e responder quesitos periciais estruturados com citações precisas dos itens probatórios.
* **Caso por conversa:** A configuração tem só `--stdio`, sem caso. Cada conversa começa sem caso aberto: o agente chama `get_server_status`, pede a pasta do caso e a abre com `open_case`, que também serve para trocar de caso. O caso de uma conversa não muda o de outra. O argumento `--case` de versões anteriores é ignorado e pode ser removido.
* **Metodologia de Custódia:** O agente opera sob estrito modo somente-leitura, podendo registrar achados relevantes com `add_to_bookmark` e `set_item_checked` para conferência posterior pelo perito no IPED Desktop.

---

## ➡️ Próximo passo

Com o servidor `iped` configurado, siga para **[Como Investigar com o MCP](Como-Investigar-com-o-MCP)**: o passo a passo para iniciar o `start_case`, abrir o caso, responder ao alerta de conteúdo sensível e conduzir a investigação.
