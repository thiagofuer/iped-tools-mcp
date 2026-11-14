# Boas Práticas Forenses e Cadeia de Custódia

O **IPED Tools MCP** foi projetado desde sua concepção para atender às exigências de laboratórios periciais criminais, perícias judiciais e processos de conformidade corporativa rigorosos.

A preservação da integridade da evidência digital e a rastreabilidade metodológica são garantidas por salvaguardas técnicas estruturais:

---

## 🛡️ 1. Acesso Estritamente Somente-Leitura (Read-Only)

* **Imutabilidade Probatória:** O leitor de índices Apache Lucene e as conexões internas com bancos de dados SQLite operam exclusivamente em modo de leitura (`ReadOnly`).
* **Preservação de Evidências Brutas:** Nenhum arquivo original extraído de discos, celulares ou imagens periciais sofre qualquer modificação, regravação ou alteração de data/hora (*MAC timestamps*).
* **Concorrência Segura:** O servidor pode ser utilizado concorrentemente com o IPED Desktop sem risco de corrupção ou travamento de índices.

---

## 🔒 2. Operação 100% Desconectada (Air-Gapped & Offline)

* **Sem Portas de Rede:** O servidor MCP **não abre portas TCP/UDP de escuta** e não hospeda servidores HTTP locais.
* **Comunicação por Pipes Locais:** Toda a troca de mensagens ocorre estritamente pelos canais locais de entrada e saída padrão (`stdin` e `stdout`) gerenciados pelo sistema operacional entre o processo pai (cliente de IA) e o processo filho (IPED Tools MCP).
* **Sem Telemetria ou Chamadas Externas:** O software não coleta dados anônimos de uso, não realiza verificações de atualização automáticas pela internet e não envia dados do caso para servidores remotos.
* **Compatibilidade Air-Gapped:** Em laboratórios sem conexão à internet, a combinação do IPED Tools MCP com o **LM Studio** e modelos de código aberto locais garante total conformidade com normas de sigilo e segurança da informação.

---

## ⚖️ 3. Únicas Modificações Permitidas: Auditoria e Triagem

Para que as descobertas da IA tenham utilidade prática no fluxo pericial oficial, o servidor permite exclusivamente duas ações de persistência:

1. **Marcadores Periciais (`bookmarks.iped`):**
   * A ferramenta `add_to_bookmark` permite agrupar IDs de documentos relevantes sob nomes de marcadores escolhidos pelo perito (ex: *"Contratos Suspeitos"* ou *"Comunicações com o Fornecedor"*).
2. **Status de Conferência (`set_item_checked`):**
   * A ferramenta `set_item_checked` alterna o estado da caixa de seleção (*checkbox*) do item, idêntica à coluna de conferência do IPED Desktop.

> 🔍 **Revisão Humana Obrigatória:** Ambas as modificações são gravadas diretamente no arquivo `iped/bookmarks.iped` do caso e são imediatamente visíveis no IPED Desktop oficial, onde o perito humano fará a conferência detalhada de cada item antes de sua inclusão no laudo pericial definitivo.

---

## 🧭 4. Regras de Conduta da IA

O prompt `start_case` e as instruções que o servidor envia ao cliente na conexão orientam a IA a seguir quatro regras:

1. **Checagem de conteúdo sensível:** antes de analisar itens, a IA conta (sem abrir os itens) os sinais de CSAM e de nudez existentes no caso e alerta o perito. Sinais cujos módulos não foram executados são relatados como "não verificados", nunca como zero.
2. **Consentimento em modelos de nuvem:** se a IA roda num provedor de nuvem, ou não sabe onde roda, ela avisa que prosseguir envia dados do caso ao provedor e que conteúdo sinalizado pode violar os termos de uso dele, e só continua com a concordância expressa do perito. Em modelos locais, prosseguir também é decisão do perito.
3. **Aceite por sessão:** o aceite vale para a conversa atual; ao trocar de caso ou iniciar outra conversa, a IA pede de novo.
4. **Confirmação de escritas:** antes de criar marcadores ou marcar itens, a IA mostra os itens e o nome do marcador e aguarda a confirmação do perito. O nome do marcador é o que o perito escolher, sem indicar autoria de IA.

> ⚠️ **Orientação, não bloqueio:** essas regras orientam a IA, mas o servidor não bloqueia chamadas. A garantia final continua sendo a revisão humana no IPED Desktop. Para que as regras completas entrem na conversa, acione o `start_case` pelo menu de prompts do cliente (ver [Fluxo de Investigação](Fluxo-de-Investigacao)).

---

## 🚫 5. Fronteiras Operacionais e Não-Metas (Non-Goals)

Para manter o assistente MCP leve, performático e seguro, foram deliberadamente estabelecidos os seguintes limites:

* **Não-Meta 1: Exportação Física em Massa:** O IPED Tools MCP não é uma ferramenta para descompactar ou exportar terabytes de arquivos físicos para o disco do usuário. Essa tarefa continua sob responsabilidade do IPED Desktop.
* **Não-Meta 2: Geração de Laudos Oficiais em PDF/HTML:** A diagramação formal e a assinatura digital do Laudo Pericial pertencem ao fluxo de trabalho oficial do perito no IPED Desktop.
* **Não-Meta 3: Execução de Comandos no Host:** O servidor proíbe terminantemente que a IA tente invocar ferramentas de linha de comando (`dir`, `ls`, scripts de sistema) diretamente no disco da máquina para ler dados do caso, forçando o uso exclusivo das 26 ferramentas MCP auditáveis.

---

## 📋 6. Padrão de Citação de Evidências

Ao redigir análises ou respostas baseadas nas informações retornadas pelas ferramentas, a IA é orientada pelo prompt `start_case` a sempre estruturar a citação com os quatro pilares periciais:

1. **ID do Item:** Identificador numérico único no IPED (ex: `ID 25830`).
2. **Nome do Arquivo e Caminho:** Localização lógica na estrutura da evidência (ex: `/DCIM/Camera/IMG_20240501.jpg`).
3. **Data e Hora (Timestamp UTC / ISO-8601):** Momento do fato ou da modificação.
4. **Assinatura Hash:** Hash criptográfico do item (SHA-256 ou MD5), assegurando a autenticidade jurídica da prova.
