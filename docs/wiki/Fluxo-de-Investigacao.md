# Metodologia e Fluxo de Investigação Forense

O **IPED Tools MCP** foi concebido sob os princípios da norma internacional de computação forense **ISO/IEC 27037** (Diretrizes para identificação, coleta, aquisição e preservação de evidências digitais).

O modelo de linguagem atua como um assistente cognitivo do examinador: ele realiza buscas, analisa metadados, correlaciona eventos no tempo e sugere linhas investigativas, enquanto **o perito mantém o controle metodológico e a decisão decisória final**.

---

## 🧭 Metodologia em 7 Etapas

Quando o prompt oficial `start_case` é acionado, a IA é instruída a seguir o seguinte ciclo de trabalho. No texto do prompt, as etapas 2 e 7 formam o bloco fixo de **regras de conduta**, que o aplicativo acrescenta sempre ao fim do prompt; as demais etapas formam a **metodologia**, que pode ser personalizada (veja [Personalizar a metodologia](#personalizar-a-metodologia)).

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
A sessão sempre se inicia verificando se há um caso IPED aberto nesta conversa (`get_server_status`). Cada conversa tem o seu próprio caso e começa sem caso aberto: nesse caso, a IA pede ao examinador o caminho da pasta do caso e o abre com `open_case`. Em seguida vem a análise volumétrica do acervo (`get_case_summary`).

### 2. Regras de Conduta
Antes de analisar qualquer item, a IA cumpre quatro regras:

* **Checagem de conteúdo sensível, só com contagens.** A IA confere com `list_properties` quais sinais existem no caso e conta, com `search_documents(limit=1)`, os itens sinalizados como CSAM (hash, detector de CSAM e base de hashes) e como nudez (DIE e NSFW). Um sinal cuja propriedade não existe no caso é relatado como **não verificado**, nunca como zero. A checagem não abre itens nem miniaturas.
* **Alerta e consentimento.** A IA apresenta as contagens. Se roda num provedor de nuvem, ou não sabe onde roda, ela avisa que prosseguir envia texto, metadados e imagens do caso ao provedor, e que conteúdo sinalizado pode violar os termos de uso dele; só continua com a concordância expressa do perito. Num modelo local, prosseguir também é decisão do perito.
* **Aceite por sessão.** O aceite vale para a conversa atual. Ao trocar de caso com `open_case`, ou numa nova conversa, a IA pede de novo.
* **Confirmação de escritas.** Antes de criar, alterar ou excluir marcadores, ou marcar itens, a IA mostra o que vai fazer e aguarda a confirmação do perito (etapa 7).

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
A IA nunca escreve no caso por iniciativa própria. Quando encontra itens de valor probatório, ela sugere registrá-los. Antes de associá-los a um marcador (`add_to_bookmark`) ou marcá-los na triagem (`set_item_checked`), mostra os itens e o nome do marcador e aguarda a confirmação do perito. O nome, a cor e o comentário do marcador são os que o perito escolher, sem termos que indiquem autoria de IA. Para a cor, a IA pode sugerir uma cor livre da paleta do IPED (`list_bookmarks`); sem escolha, o marcador é criado sem cor.

O registro pode ser corrigido pelo chat, sempre com confirmação: `list_bookmark_items` mostra o que está num marcador, `remove_from_bookmark` tira itens incluídos por engano, `update_bookmark` muda nome, cor ou comentário, e `delete_bookmark` exclui o marcador. Na remoção e na exclusão, a IA mostra o que sai e avisa que a mudança não pode ser desfeita pelo MCP.

---

## 📜 O Prompt Oficial: `start_case`

O servidor disponibiliza através da especificação MCP o prompt canônico `start_case`.

### Como acionar no cliente:
O `start_case` é um **prompt MCP**: quem o insere na conversa é o cliente, quando você o escolhe na interface. A IA não consegue acioná-lo sozinha.
* **No Claude Desktop:** abra o menu **+** da caixa de mensagem, escolha o servidor `iped` e selecione `start_case`. Preencha os parâmetros opcionais e envie. O texto do prompt aparece anexado à mensagem antes do envio.
* **Em outros clientes:** use o menu ou a lista de prompts MCP do cliente, se ele oferecer.

> ⚠️ **Não digite `start_case` como texto comum** (nem peça "execute o prompt start_case"). Assim a mensagem chega à IA como um pedido qualquer, e a metodologia e as regras de conduta não entram na conversa.

Mesmo sem o `start_case`, o servidor envia ao cliente, na conexão, um resumo das regras de conduta (as *instructions* do protocolo MCP). Cada cliente decide se repassa esse texto à IA. Nos clientes testados, só o Claude Code o repassa. O chat do Claude Desktop e o LM Studio não repassam: neles, só a regra de confirmar escritas chega à IA, pelas descrições das ferramentas. Por isso, acione o `start_case` pelo menu de prompts.

### Clientes sem menu de prompts:
Se o cliente não oferece os prompts MCP, abra o [Configurador Gráfico](Configurador-Grafico), seção **Prompt inicial da investigação**, preencha se quiser o foco e a pasta do caso e clique em **📋 Copiar prompt**. Cole o texto como **primeira mensagem** da conversa. É o mesmo texto que o servidor entrega no `start_case`, com a metodologia em uso e as regras de conduta.

### Parâmetros Opcionais:
* `investigation_target`: Tema ou foco específico da investigação (ex: *"mensagens entre o suspeito A e o suspeito B"*).
* `case_path`: Caminho absoluto opcional do caso, caso queira que a IA invoque `open_case` automaticamente.

### Espelhamento Dinâmico de Idioma (Dynamic Language Mirroring):
O prompt instrui a IA a iniciar respondendo em **Português do Brasil (pt-BR)** como idioma padrão, mas a acompanhar fluentemente qualquer outro idioma utilizado pelo examinador ao longo do diálogo, mantendo inalterados termos técnicos internacionais e hashes forenses.

### Personalizar a metodologia
O `start_case` tem três partes, sempre nesta ordem:

1. **Metodologia:** persona, idioma e as etapas de trabalho. É a parte que pode ser personalizada.
2. **Foco e pasta do caso:** gerados a partir dos parâmetros opcionais.
3. **Regras de conduta:** acesso só pelo MCP, checagem de conteúdo sensível, consentimento e aceite, e confirmação de escritas. Elas são acrescentadas pelo aplicativo, **não fazem parte do arquivo editável** e prevalecem sobre qualquer instrução anterior do prompt.

Para adaptar a metodologia à rotina do laboratório, use o botão **Editar** do [Configurador Gráfico](Configurador-Grafico). A cópia personalizada fica em `%USERPROFILE%\.iped-tools-mcp\prompts\start_case.md` e vale no próximo `start_case`, sem reiniciar o cliente de IA. **Restaurar padrão** volta à metodologia da versão instalada e guarda a cópia como backup.

Recomendações:
* Mantenha a metodologia enxuta. Modelos locais têm contexto pequeno, e o prompt inteiro entra na conversa. A metodologia padrão tem cerca de 6 KB; o limite aceito é 200 KB, mas textos muito longos prejudicam a análise.
* As descrições das ferramentas continuam orientando o modelo (paginação, descoberta de propriedades etc.) mesmo que a metodologia personalizada não cite esses pontos.
* As regras de conduta orientam a IA, mas o servidor não bloqueia chamadas: um texto da metodologia que as contradiga não as desliga, porém também não há garantia de que o modelo as siga.

**Cópia desatualizada:** uma nova versão pode mudar a metodologia padrão (por exemplo, ao incluir ou renomear ferramentas). A cópia personalizada registra de qual padrão foi criada, e o configurador avisa quando ela ficou desatualizada. Compare a cópia com o padrão (o backup criado por **Restaurar padrão** ajuda) e atualize-a, ou restaure o padrão.

---

## 🔍 Ciclo Fechado com o IPED Desktop

O trabalho realizado pela IA não fica isolado no chat:

1. A IA encontra uma evidência crítica, mostra ao perito o item e o nome do marcador e, com a confirmação dele, executa:
   ```json
   add_to_bookmark(doc_ids=[10425], bookmark_name="Evidências Relevantes - Operação", color="#CF000F")
   set_item_checked(item_id=10425, checked=true)
   ```
2. O servidor salva o estado diretamente no arquivo `iped/bookmarks.iped` do caso antes de responder.
3. Ao abrir o caso no **IPED Desktop oficial**, o perito verá a evidência marcada na árvore de marcadores, na cor escolhida, e com o checkbox de conferência selecionado. Se o IPED Desktop já estava com o caso aberto, feche e reabra o caso para ver as mudanças feitas pelo chat.
4. O perito realiza a revisão humana, confere a cadeia de custódia e exporta o laudo oficial em HTML ou PDF com total segurança jurídica.
