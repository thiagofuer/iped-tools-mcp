# Como Investigar com o MCP

Esta página ensina a conduzir a investigação de um caso processado pelo IPED usando o **IPED Tools MCP** com o aplicativo de IA que você configurou. O modelo de linguagem atua como assistente: faz buscas, analisa metadados, correlaciona eventos no tempo e sugere linhas investigativas, enquanto **o perito mantém o controle metodológico e a decisão final**. A metodologia segue os princípios da norma **ISO/IEC 27037**.

A página tem três partes:

1. [Passo a passo](#parte-1-passo-a-passo): o que você faz na conversa, do início ao laudo.
2. [O que a IA faz](#parte-2-o-que-a-ia-faz): a metodologia e as regras de conduta que o modelo segue.
3. [Personalizar a metodologia](#parte-3-personalizar-a-metodologia): como adaptar o prompt à rotina do laboratório.

---

## Parte 1: Passo a passo

### Antes de começar
* O aplicativo de IA já está configurado com o servidor `iped` (veja [Configurador Gráfico](Configurador-Grafico) e o guia do seu cliente no menu ao lado).
* Você sabe o caminho da **pasta raiz do caso** processado pelo IPED (a pasta que contém a subpasta `iped`), por exemplo `C:\casos_forenses\caso_operacao_01`.
* Se quiser conferir a instalação antes, rode **Testar o MCP** no configurador com essa pasta.

### 1. Inicie a conversa com o prompt `start_case`
Abra uma **conversa nova** e comece pelo `start_case`. Ele traz a metodologia de investigação e as regras de conduta para a conversa.

| Cliente | Como iniciar o `start_case` |
|---|---|
| **Claude Desktop** | Menu **+** da caixa de mensagem → servidor `iped` → `start_case`. Preencha os parâmetros opcionais e envie. |
| **Claude Code** | Digite `/mcp__iped__start_case` (digite `/` e procure por `start_case` para conferir o nome na sua instalação). |
| **LM Studio** | Nas versões testadas, o LM Studio não lista prompts MCP: use o **Copiar prompt** do configurador (abaixo). Se a sua versão listar os prompts do servidor `iped`, selecione `start_case`. |
| **Cursor** e **Antigravity** | Use a lista de prompts MCP do cliente, se ele oferecer. Se não oferecer, use o **Copiar prompt** do configurador (abaixo). |
| **Qualquer cliente sem prompts MCP** | No [Configurador Gráfico](Configurador-Grafico), seção **Prompt inicial da investigação**, preencha se quiser o foco e a pasta do caso, clique em **📋 Copiar prompt** e cole o texto como **primeira mensagem** da conversa. |

> ⚠️ **Não digite `start_case` como texto comum** (nem peça "execute o prompt start_case"). Assim a mensagem chega à IA como um pedido qualquer, e a metodologia e as regras de conduta não entram na conversa.

Parâmetros opcionais do `start_case`:
* `investigation_target`: tema ou foco da investigação (ex.: *"mensagens entre o suspeito A e o suspeito B"*).
* `case_path`: caminho da pasta do caso, para a IA abri-lo logo no início.

Mesmo sem o `start_case`, o servidor envia ao cliente, na conexão, um resumo das regras de conduta (as *instructions* do protocolo MCP). Cada cliente decide se repassa esse texto à IA. Nos clientes testados, só o Claude Code o repassa; o chat do Claude Desktop e o LM Studio não. Por isso, sempre comece pelo `start_case`.

### 2. Informe a pasta do caso
Cada conversa tem o seu próprio caso e começa **sem caso aberto**. Se você não informou `case_path`, a IA pede o caminho da pasta. Responda, por exemplo:
> *"O caso está em C:\casos_forenses\caso_operacao_01"*

A IA abre o caso com `open_case` e apresenta um resumo: total de itens, categorias e marcadores já existentes. Para trocar de caso na mesma conversa, peça *"abra o caso em ..."*.

### 3. Responda ao alerta de conteúdo sensível
Antes de analisar qualquer item, a IA **conta** (sem abrir) os itens sinalizados como CSAM ou nudez e mostra as contagens. Um sinal cujo módulo não foi executado no processamento aparece como **não verificado**, e não como zero.
* **Modelo em nuvem** (ou na dúvida): a IA avisa que prosseguir envia texto, metadados e imagens do caso ao provedor do modelo, e que conteúdo sinalizado pode violar os termos de uso dele. **Ela só continua se você concordar expressamente.** Se não concordar, use um modelo local (por exemplo, no LM Studio).
* **Modelo local:** a IA mostra as contagens e pergunta se deve prosseguir quando há itens sinalizados.

O aceite vale para a conversa atual. Ao trocar de caso ou abrir uma conversa nova, a IA pergunta de novo.

### 4. Faça perguntas
Converse em linguagem natural. Alguns exemplos:
* *"Resuma este caso: fontes de evidência, categorias principais e período coberto."*
* *"Quais identificadores do aparelho e das contas foram registrados? Quem parece usar o aparelho?"*
* *"Quem são os contatos mais frequentes no WhatsApp? Mostre a relação entre o contato X e o contato Y."*
* *"Monte a linha do tempo de 01/06 a 05/06 e destaque os eventos perto das 22h do dia 03."*
* *"Busque mensagens que mencionem 'transferência' ou 'pix' e mostre as 10 primeiras com data e remetente."*
* *"Há imagens com texto de comprovante? Mostre a imagem do item 1234 em tamanho legível."*

A IA cita o **ID do item**, o nome, o caminho, a data e o hash das evidências, para você conferir e levar ao laudo. Para ver o que cada ferramenta faz, consulte o [Catálogo de Ferramentas](Catalogo-de-Ferramentas); para escrever buscas precisas, [Consultas Lucene e Propriedades](Consultas-Lucene-e-Propriedades).

### 5. Registre os achados
A IA nunca escreve no caso por iniciativa própria. Quando encontra algo relevante, ela **propõe** registrar e mostra os itens, o nome do marcador e, se houver, a cor e o comentário. **Você escolhe** o nome, a cor e o comentário (a IA pode sugerir) e confirma. Só então ela grava.
* Para corrigir: peça para listar os itens de um marcador, tirar itens incluídos por engano, renomear, mudar a cor ou o comentário, ou excluir o marcador. Remoções e exclusões são mostradas antes e não podem ser desfeitas pelo MCP.
* Também é possível marcar itens como conferidos (o *checkbox* do IPED), sempre com confirmação.

### 6. Revise no IPED Desktop
O trabalho da IA não fica isolado no chat:

1. Com a sua confirmação, a IA grava o marcador e as marcações, por exemplo:
   ```json
   add_to_bookmark(doc_ids=[10425], bookmark_name="Evidências Relevantes - Operação", color="#CF000F")
   set_item_checked(item_id=10425, checked=true)
   ```
2. O servidor salva o estado diretamente no arquivo `iped/bookmarks.iped` do caso antes de responder.
3. Ao abrir o caso no **IPED Desktop**, você verá as evidências na árvore de marcadores, na cor escolhida, com o checkbox de conferência selecionado. Se o IPED Desktop já estava com o caso aberto, feche e reabra o caso para ver as mudanças.
4. Faça a revisão humana, confira a cadeia de custódia e gere o laudo pelo IPED.

---

## Parte 2: O que a IA faz

Quando o `start_case` é acionado, a IA segue o ciclo abaixo. No texto do prompt, as etapas 2 e 7 formam o bloco fixo de **regras de conduta**, que o aplicativo acrescenta sempre ao fim do prompt; as demais etapas formam a **metodologia**, que pode ser personalizada (veja a [Parte 3](#parte-3-personalizar-a-metodologia)).

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
* Para mídias visuais, obtém-se a imagem em JPEG (`get_item_thumbnail`).

### 7. Registro Pericial com Confirmação
A IA nunca escreve no caso por iniciativa própria. Quando encontra itens de valor probatório, ela sugere registrá-los. Antes de associá-los a um marcador (`add_to_bookmark`) ou marcá-los na triagem (`set_item_checked`), mostra os itens e o nome do marcador e aguarda a confirmação do perito. O nome, a cor e o comentário do marcador são os que o perito escolher, sem termos que indiquem autoria de IA. Para a cor, a IA pode sugerir uma cor livre da paleta do IPED (`list_bookmarks`); sem escolha, o marcador é criado sem cor.

O registro pode ser corrigido pelo chat, sempre com confirmação: `list_bookmark_items` mostra o que está num marcador, `remove_from_bookmark` tira itens incluídos por engano, `update_bookmark` muda nome, cor ou comentário, e `delete_bookmark` exclui o marcador. Na remoção e na exclusão, a IA mostra o que sai e avisa que a mudança não pode ser desfeita pelo MCP.

### Idioma
O prompt instrui a IA a iniciar respondendo em **Português do Brasil (pt-BR)**, mas a acompanhar qualquer outro idioma usado pelo examinador ao longo do diálogo, mantendo inalterados termos técnicos internacionais e hashes.

---

## Parte 3: Personalizar a metodologia

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
