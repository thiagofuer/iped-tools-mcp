# Configuração no LM Studio (Ambientes Air-Gapped & Offline)

O [LM Studio](https://lmstudio.ai/) é a **solução recomendada para laboratórios periciais e gabinetes em redes isoladas (air-gapped)**, onde nenhuma evidência digital ou metadado pericial pode transitar pela internet.

Com o LM Studio, você executa modelos de linguagem de ponta (como Llama 3, Qwen 2.5, Mistral ou DeepSeek) rodando 100% localmente na GPU/CPU da sua estação forense, conectando-os diretamente ao IPED Tools MCP via canal seguro STDIO.

---

## 📋 Método 1: Formulário Visual do LM Studio (Recomendado)

O [Configurador Gráfico](Configurador-Grafico) gera os campos exatos para preenchimento na interface visual do LM Studio:

1. No LM Studio, acesse a aba lateral **MCP** (ou Program / MCP Servers).
2. Clique em **Add MCP Server** (ou ícone `+`).
3. Preencha os campos exatamente como indicado:
   * **Nome (Name):** `iped`
   * **Conexão (Connection):** `Neste computador` (*Local*)
   * **Comando (Command):** `C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe`
   * **Argumentos (Arguments):** Clique em **+ Adicionar argumento** e insira somente:
     ```text
     --stdio
     ```
4. Salve e ative a chave de conexão correspondente.

> ✨ **Vantagem:** Com apenas o argumento `--stdio`, você nunca mais precisará reconfigurar o LM Studio! O servidor se sincronizará automaticamente com qualquer caso selecionado no Configurador Gráfico ou aberto pela IA no chat.

---

## 📄 Método 2: Arquivo de Configuração (`ng-mcp.json`)

Se você preferir configurar inserindo o bloco JSON diretamente no arquivo do LM Studio (ou através do botão "Edit JSON"):

```json
{
  "servers": [
    {
      "id": "iped-tools-mcp",
      "name": "iped",
      "enabled": true,
      "connection": {
        "type": "stdio",
        "command": "C:\\Program Files\\IPED Tools MCP\\IPED-Tools-MCP.exe",
        "args": [
          "--stdio"
        ],
        "env": {}
      }
    }
  ]
}
```

---

## 🔒 Rigor em Redes Isoladas (Air-Gapped)

Ao operar em redes desconectadas:
* **Sem vazamento de dados:** O IPED Tools MCP não abre portas de rede nem realiza telemetria; os dados trafegam unicamente pelos descritores locais `stdin`/`stdout`.
* **Sem dependência de nuvem:** O modelo de IA roda nativamente no LM Studio e interroga os índices Lucene e SQLite locais na memória do computador.
* **Prompt Forense:** O `start_case` é um prompt MCP e precisa ser acionado pela lista de prompts do cliente, não digitado como texto. Nas versões testadas, o LM Studio não lista prompts MCP nem repassa ao modelo o resumo das regras de conduta (as *instructions* do protocolo MCP). Digitar `/start_case` não tem efeito. Nesse caso, só a regra de confirmar escritas chega ao modelo, pelas descrições das ferramentas: antes de analisar itens, peça explicitamente a checagem de conteúdo sensível descrita em [Fluxo de Investigação](Fluxo-de-Investigacao). Se a sua versão listar os prompts do servidor `iped`, selecione `start_case` ao iniciar a conversa.
