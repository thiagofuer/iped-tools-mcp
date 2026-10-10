# Configuração no Antigravity

O **Antigravity** integra o Model Context Protocol (MCP), provendo um ambiente avançado de agentes autônomos para perícias forenses complexas, análise automatizada de vestígios e cruzamento de evidências do IPED.

---

## 📍 Configuração do Servidor MCP

No Antigravity, acesse as configurações de servidores MCP através do menu de preferências ou editando diretamente o arquivo de configuração MCP do ambiente (ou no arquivo de workspace correspondente):

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

*(Se estiver utilizando a versão portátil, utilize o caminho correspondente: `"C:\\Ferramentas\\IPED-Tools-MCP\\IPED-Tools-MCP.exe"`)*

---

## 🛠️ Benefícios no Antigravity

Com o servidor conectado no Antigravity:
* **Automação de Quesitos:** Agentes especializados podem ler volumes maciços de evidências indexadas, checar integridade de hashes e responder quesitos periciais estruturados com citações precisas dos itens probatórios.
* **Sincronização Compartilhada:** Não é necessário fixar o parâmetro `--case`. O agente pode iniciar chamando `check_connection` ou `get_server_status` para identificar o caso ativo, ou alternar entre fontes probatórias utilizando `open_case`.
* **Metodologia de Custódia:** O agente opera sob estrito modo somente-leitura, podendo registrar achados relevantes com `add_to_bookmark` e `set_item_checked` para conferência posterior pelo perito no IPED Desktop.
