# Metodologia e Fluxo de Investigação Forense

O **IPED Tools MCP** foi concebido sob os princípios da norma internacional de computação forense **ISO/IEC 27037** (Diretrizes para identificação, coleta, aquisição e preservação de evidências digitais).

O modelo de linguagem atua como um assistente cognitivo do examinador: ele realiza buscas, analisa metadados, correlaciona eventos no tempo e sugere linhas investigativas, enquanto **o perito mantém o controle metodológico e a decisão decisória final**.

---

## 🧭 Metodologia em 7 Etapas

Quando o prompt oficial `start_case` é acionado, a IA é instruída a seguir o seguinte ciclo de trabalho:

```
[1. Status e Prontidão]  -->  [2. Regras de Conduta]          -->  [3. Reconhecimento de Fontes]
      get_server_status        checagem de conteúdo sensível        list_sources
      get_case_summary         alerta, consentimento e aceite       get_device_and_accounts
             |
             v
[4. Propriedades do Caso] --> [5. Busca Estruturada] --> [6. Inspeção Progressiva] --> [7. Registro com Confirmação]
      list_properties           search_documents          get_metadata / get_text       add_to_bookmark
      describe_property         get_timeline              get_item_thumbnail            set_item_checked
```

### 1. Verificação do Ambiente e Caso
A sessão sempre se inicia verificando se há um caso IPED carregado e pronto (`get_server_status`), seguido da análise volumétrica do acervo (`get_case_summary`).

### 2. Regras de Conduta
Antes de analisar qualquer item, a IA cumpre quatro regras:

* **Checagem de conteúdo sensível, só com contagens.** A IA confere com `list_properties` quais sinais existem no caso e conta, com `search_documents(limit=1)`, os itens sinalizados como CSAM (hash, detector de CSAM e base de hashes) e como nudez (DIE e NSFW). Um sinal cuja propriedade não existe no caso é relatado como **não verificado**, nunca como zero. A checagem não abre itens nem miniaturas.
* **Alerta e consentimento.** A IA apresenta as contagens. Se roda num provedor de nuvem, ou não sabe onde roda, ela avisa que prosseguir envia texto, metadados e imagens do caso ao provedor, e que conteúdo sinalizado pode violar os termos de uso dele; só continua com a concordância expressa do perito. Num modelo local, prosseguir também é decisão do perito.
* **Aceite por sessão.** O aceite vale para a conversa atual. Ao trocar de caso com `open_case`, ou numa nova conversa, a IA pede de novo.
* **Confirmação de escritas.** Antes de criar marcadores ou marcar itens, a IA mostra o que vai fazer e aguarda a confirmação do perito (etapa 7).

Essas regras orientam a IA; o servidor não bloqueia chamadas. Por isso o perito continua revisando os marcadores no IPED Desktop.

### 3. Reconhecimento de Fontes de Evidência
O perito e a IA identificam as mídias periciadas (`list_sources`) e levantam os identificadores registrados do aparelho e das contas (`get_device_and_accounts`), como números de telefone, IMEI e e-mails, cada um com os itens de origem. O servidor não aponta o dono: para concluir quem usa o aparelho, a IA compara as fontes (identificadores sustentados por vários aplicativos pesam mais), aprofunda com `list_properties(identity=true)` e `describe_property` e cita os itens que sustentam a conclusão.

### 4. Descoberta das Propriedades do Caso
Para evitar alucinações de campos e erros de sintaxe, a IA descobre as propriedades que existem **neste** caso antes de formular consultas sobre chats, EXIF, sistemas de arquivos ou resultados de IA: `list_properties` mostra os grupos e as propriedades com contagens exatas, e `describe_property` explica o significado, a escala e os valores de cada uma. Uma propriedade que não aparece no catálogo não existe no caso (o módulo que a gera pode não ter sido executado), o que é diferente de zero itens.

### 5. Busca Estruturada e Recuperação
Execução de pesquisas com sintaxe Apache Lucene (`search_documents`), inclusive sobre os resultados de IA do grupo `AI` (ex.: `nudityScore:[650 TO *]`, `faceAge\:labels:child`), correlação temporal (`get_timeline`, `get_events_around_time`) ou análise de vínculos interpessoais (`get_communications_graph`, `get_top_contacts`).

### 6. Inspeção Progressiva de Conteúdo
Para economizar tokens e evitar estouro de contexto:
* Primeiro, inspecionam-se os metadados forenses sumarizados (`get_document_metadata`).
* Se houver relevância, extrai-se o texto paginado (`get_document_text`).
* Para mídias visuais, obtém-se a miniatura gráfica em Base64 JPEG (`get_item_thumbnail`).

### 7. Registro Pericial com Confirmação
A IA nunca escreve no caso por iniciativa própria. Quando encontra itens de valor probatório, ela sugere registrá-los. Antes de associá-los a um marcador (`add_to_bookmark`) ou marcá-los na triagem (`set_item_checked`), mostra os itens e o nome do marcador e aguarda a confirmação do perito. O nome do marcador é o que o perito escolher, sem termos que indiquem autoria de IA.

---

## 📜 O Prompt Oficial: `start_case`

O servidor disponibiliza através da especificação MCP o prompt canônico `start_case`.

### Como acionar no cliente:
O `start_case` é um **prompt MCP**: quem o insere na conversa é o cliente, quando você o escolhe na interface. A IA não consegue acioná-lo sozinha.
* **No Claude Desktop:** abra o menu **+** da caixa de mensagem, escolha o servidor `iped` e selecione `start_case`. Preencha os parâmetros opcionais e envie. O texto do prompt aparece anexado à mensagem antes do envio.
* **Em outros clientes:** use o menu ou a lista de prompts MCP do cliente, se ele oferecer.

> ⚠️ **Não digite `start_case` como texto comum** (nem peça "execute o prompt start_case"). Assim a mensagem chega à IA como um pedido qualquer, e a metodologia e as regras de conduta não entram na conversa.

Mesmo sem o `start_case`, o servidor envia ao cliente, na conexão, um resumo das regras de conduta (as *instructions* do protocolo MCP). Cada cliente decide se repassa esse texto à IA, por isso acionar o `start_case` continua sendo o caminho recomendado.

### Parâmetros Opcionais:
* `investigation_target`: Tema ou foco específico da investigação (ex: *"mensagens entre o suspeito A e o suspeito B"*).
* `case_path`: Caminho absoluto opcional do caso, caso queira que a IA invoque `open_case` automaticamente.

### Espelhamento Dinâmico de Idioma (Dynamic Language Mirroring):
O prompt instrui a IA a iniciar respondendo em **Português do Brasil (pt-BR)** como idioma padrão, mas a acompanhar fluentemente qualquer outro idioma utilizado pelo examinador ao longo do diálogo, mantendo inalterados termos técnicos internacionais e hashes forenses.

---

## 🔍 Ciclo Fechado com o IPED Desktop

O trabalho realizado pela IA não fica isolado no chat:

1. A IA encontra uma evidência crítica, mostra ao perito o item e o nome do marcador e, com a confirmação dele, executa:
   ```json
   add_to_bookmark(doc_ids=[10425], bookmark_name="Evidências Relevantes - Operação")
   set_item_checked(item_id=10425, checked=true)
   ```
2. O servidor salva o estado diretamente no arquivo `iped/bookmarks.iped` do caso.
3. Ao abrir o caso no **IPED Desktop oficial**, o perito verá a evidência imediatamente marcada na árvore de marcadores e com o checkbox de conferência selecionado.
4. O perito realiza a revisão humana, confere a cadeia de custódia e exporta o laudo oficial em HTML ou PDF com total segurança jurídica.
