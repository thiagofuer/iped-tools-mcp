# Changelog

Todas as alterações notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

---

## [Unreleased]

---

## [2.0.0] - 2026-11-23

### Adicionado
- **Guia do Codex (app da OpenAI):** sexto cliente homologado. O servidor funciona nas conversas do modo Codex, configurado em Plugins → MCPs ou no `config.toml`; o chat comum do ChatGPT, no mesmo app, não usa servidores MCP locais. Como o Codex não lista prompts MCP, a investigação começa pelo **Copiar prompt** do configurador. O guia explica que as instâncias que iniciam e param em menos de 1 segundo no log são checagens de status do app. O Codex entrou no menu da wiki, na página inicial, em Requisitos, no README e na tabela de "Como Investigar com o MCP". O configurador ganhou a opção **Codex (app da OpenAI)**, que gera os campos de Plugins → MCPs e o trecho do `config.toml`, com o aviso sobre o chat comum do ChatGPT e a dica do Copiar prompt.
- **Prompt `start_case` personalizável:** o texto sai do código e passa a ter três partes: a metodologia, as seções geradas (foco e pasta do caso) e, por último, as regras de conduta.
  - A metodologia padrão é um recurso do executável. O perito pode criar uma cópia em `%USERPROFILE%\.iped-tools-mcp\prompts\start_case.md`, que o servidor lê a cada `start_case`, sem reinício. Uma cópia vazia, com mais de 200 KB, fora do UTF-8 ou ilegível é ignorada, com aviso no log, e vale o padrão.
  - As regras de conduta (acesso só pelo MCP, checagem de conteúdo sensível, consentimento e aceite, confirmação de escritas) não fazem parte do arquivo editável: o aplicativo as acrescenta sempre ao fim do prompt, e elas prevalecem sobre o restante do texto.
  - A cópia registra de qual padrão foi criada; quando uma nova versão muda o padrão, o configurador e o log avisam que ela está desatualizada.
- **Seção "Prompt inicial da investigação" no configurador:** mostra a metodologia em uso; **Copiar prompt** copia o mesmo texto que o servidor entrega no `start_case`, com foco e pasta do caso opcionais, para clientes que não oferecem os prompts do MCP; **Editar** cria a cópia a partir do padrão e a abre no editor; **Abrir pasta**; **Restaurar padrão** guarda a cópia como backup e volta ao padrão.
- **Logs e diagnóstico para suporte:**
  - **Log em arquivo nos dois modos:** fica em `%USERPROFILE%\.iped-tools-mcp\logs\` (`iped-tools-mcp-stdio.log` e `iped-tools-mcp-gui.log`), com rotação a cada 5 MB (5 arquivos guardados), UTF-8 e o número do processo em cada linha. O `stderr` continua recebendo o log, e o `stdout` segue exclusivo do JSON-RPC.
  - **Cabeçalho de diagnóstico em cada início:**
    - versões, modo, argumentos, codificações, caso ativo e `iped.root`;
    - `java.library.path` e as pastas do PATH ligadas a Python, JEP ou IPED, com as DLLs encontradas.
  - **Etapas da abertura do caso** com início, fim e duração: se o processo morrer, a última linha indica a etapa.
  - **Quedas:** exceções não capturadas e o encerramento ficam registrados, e os relatórios de queda da JVM (`hs_err_pid*.log`) deixados na pasta temporária são copiados para a pasta de logs no início seguinte.
  - **`get_server_status`** passa a informar `log_file` e, quando a abertura do caso falha, `last_error` (etapa, mensagem e horário).
  - **O log não registra conteúdo do caso:** nem texto de itens, nem consultas, nem valores devolvidos. Os termos que o IPED registrava na busca de documentos similares deixam de ir para o log.
- **Modelo único de eventos de comunicação (`CommunicationAnalytics`, `CommunicationEventExtractor`):**
  - Eventos derivados somente do índice Lucene (somente leitura) a partir de itens com `Communication:From` ou `Communication:To`, com leitura seletiva dos campos armazenados e processamento em *streaming*.
  - Canal resolvido por `ufed:SourceApplication` → `accountType` → `contentType` (ex.: `Viber`, `WhatsApp`, `Telegram`, `Phone`, `Email`), com `unknown` quando não determinável.
  - Identidade das partes pelas subpropriedades estruturadas `Communication:From/To:id|name|phoneNumber` antes do parsing de texto, normalização de telefones com libphonenumber, e-mails em minúsculas, união de aliases e descarte de remetentes de sistema (`System Message`, `status@broadcast`).
  - Mensagens de grupo vinculadas ao nó de grupo (o `Communication:To:id` do chat não vira pessoa).
  - Diagnósticos de cobertura em todas as respostas (`events_analyzed`, direção explícita, canal resolvido e data).
- **Nova ferramenta MCP `get_contact_relationship`:** detalha a relação entre dois contatos (totais, canais, direções, histograma mensal, grupos em comum e `evidence_ids`), devolvendo `candidates` quando a referência é ambígua.
- **Filtros `channel`, `start_date` e `end_date`** em `get_top_contacts`, `get_communications_graph` e `get_contact_relationship`; `include_mermaid` opcional em `get_communications_graph`.
- **Catálogo de propriedades do caso (issue #2):** as propriedades passam a vir do índice do caso, com contagem exata de itens, tipo (`metadataTypes.txt`) e grupo (prefixos do IPED mais o grupo `AI`), junto com a semântica curada das propriedades conhecidas do IPED (significado, escala, valores e exemplos de consulta). Os nomes curados são verificados por teste contra as constantes do IPED.
  - **Nova ferramenta `list_properties`:** grupos do caso e, com `group` ou `pattern`, as propriedades com tipo, contagem, descrição e exemplo de consulta escapado; `category` restringe as contagens à categoria.
  - **Nova ferramenta `describe_property`:** significado, escala, mínimo e máximo (números e datas) ou valores mais frequentes (texto) e exemplos de consulta; com `query`, a distribuição é calculada só sobre os itens da consulta. Propriedade inexistente é informada como ausente (`present: false`), com sugestão de nomes parecidos, em vez de zero itens.
  - **Aviso de propriedade inexistente em `search_documents`:** `warnings` com `property_not_in_case` quando a consulta cita uma propriedade que não existe no caso.
  - **Parâmetro `groups` em `get_document_metadata`** para selecionar grupos do catálogo.
- **Paginação em `search_documents`:** novo parâmetro `offset` e campos `has_more` e `next_offset` na resposta, para percorrer todos os itens de uma consulta em páginas de até 100. A ordem é a do índice do caso, a mesma em todas as chamadas, e a nota da resposta indica as posições exibidas e como pedir a próxima página. A descrição da ferramenta e o prompt `start_case` orientam a não concluir ausência de evidência enquanto houver páginas não lidas. Chamadas sem `offset` continuam funcionando como antes.
- **Rastreabilidade e transparência:** até 5 `evidence_ids` por contato e por aresta (mais antigo, mais recente e amostra determinística), campos `truncated`, `total_eligible_edges`, `unlinked_events` e `unknown_direction_total`.
- **Regras de conduta no prompt `start_case`:** antes de analisar itens, a IA conta os sinais de CSAM e de nudez existentes no caso (sem abrir os itens; sinais de módulos não executados são relatados como "não verificados") e alerta o perito. Em modelos de nuvem, ou na dúvida, avisa que prosseguir envia dados do caso ao provedor e só continua com a concordância expressa do perito. O aceite vale por sessão, e toda escrita no caso exige confirmação. As regras orientam a IA; o servidor não bloqueia chamadas.
- **Instruções do servidor MCP:** a resposta do `initialize` passa a trazer, no campo `instructions`, um resumo das regras de conduta e a recomendação de acionar o `start_case`, para que as regras cheguem à IA mesmo quando o prompt não é acionado (depende de o cliente repassar o texto ao modelo). Para clientes sem menu de prompts, como o Codex e o LM Studio, as instruções orientam colar como primeira mensagem o texto do botão "Copiar prompt" do configurador.
- **Gestão de marcadores (paridade com o gerenciador de marcadores do IPED):**
  - **Nova ferramenta `list_bookmark_items`:** itens de um marcador (ID, nome, caminho, categoria), paginados com `total_found`, `has_more` e `next_offset`.
  - **Nova ferramenta `remove_from_bookmark`:** tira itens de um marcador, mantendo o marcador mesmo vazio.
  - **Nova ferramenta `update_bookmark`:** renomeia e altera cor (`#RRGGBB`) e comentário; recusa nome igual a outro marcador sem diferenciar maiúsculas.
  - **Nova ferramenta `delete_bookmark`:** exclui o marcador; os itens continuam no caso, com os demais marcadores e a seleção inalterados.
  - **`add_to_bookmark`:** aceita `color` e `comment` na criação, informa se o marcador foi criado (`created`) e lista os IDs inexistentes (`not_found_ids`) em vez de gravá-los. Recusa nome que difere de um marcador existente só em maiúsculas.
  - **Cor escolhida pelo usuário:** o servidor não atribui cor; a IA pode sugerir uma cor livre da paleta padrão do IPED e a cor entra na confirmação. Sem escolha, o marcador fica sem cor e o IPED Desktop o exibe na cor padrão.
  - As descrições das ferramentas, o `start_case` e as `instructions` exigem confirmação para todas as escritas em marcador; na remoção e na exclusão, a IA mostra o que sai e avisa que não há desfazer pelo MCP. O servidor passa a expor 30 ferramentas.

### Modificado
- **BREAKING — caso por sessão:** cada processo do servidor (cada conversa de cada cliente de IA) controla só o próprio caso, em memória. Uma sessão nova começa **sem caso aberto**, e o caso só é aberto pela ferramenta `open_case`. Um caso aberto numa conversa não muda mais o caso de outra conversa, de outro cliente ou do configurador. Sem caso, as ferramentas, o `get_server_status`, as instruções do servidor e o prompt `start_case` orientam o modelo a pedir a pasta do caso ao usuário e chamar `open_case`. A pré-carga em segundo plano foi removida, e o servidor não abre caso ao iniciar.
- **BREAKING — `--case` ignorado:** o argumento não é mais suportado. Configurações antigas que ainda o passam continuam iniciando: o servidor ignora o argumento, registra um aviso no log e inicia sem caso.
- **BREAKING — configurador gráfico:** a janela não abre nem define mais o caso das conversas. Saem a seleção e o carregamento de caso, o painel de estatísticas e a opção "Fixar caso no comando (--case)"; a configuração gerada tem só `--stdio`. Entra a seção **Testar o MCP**, que executa o mesmo comando gerado para o cliente, em modo STDIO, num processo separado, e mostra o resultado de `initialize`, `tools/list`, `get_server_status` e, com uma pasta de caso escolhida, `open_case` e `get_case_summary`. O teste reprova uma versão diferente da janela e qualquer saída fora do JSON-RPC no `stdout`, e tem tempo limite e botão Cancelar. A configuração `java -jar` gerada passa a incluir as opções `--add-exports` do leitor de PNG.
- **Cabeçalho de diagnóstico:** deixa de mostrar o caso ativo, já que o servidor inicia sem caso.
- **Buscas de similaridade:** o item de referência passa a vir marcado com `is_reference: true` nos resultados de `search_similar_images` e `search_similar_faces`. As três buscas ganham `similar_found`, o total de similares sem a referência, e `reference_in_results`; o `total_found` mantém o significado. A descrição de `search_similar_images` passa a orientar a leitura da pontuação: 100 indica imagem idêntica ou quase; o mínimo padrão de 1.0, o mesmo do IPED, traz tudo ordenado; um `min_score` alto (por exemplo 80) fica só com cópias e variações próximas; e ícones são más referências. Os padrões não mudam.
- **BREAKING — `list_bookmarks`:** a resposta deixa de ser uma lista de nomes e passa a ser um objeto com `bookmarks` (nome, `item_count`, `comment`, `color`), `total` e `unused_standard_colors` (cores da paleta padrão do IPED que nenhum marcador usa). O `get_case_summary` continua devolvendo a lista de nomes.
- **Marcadores relidos do disco:** antes de cada operação de marcador ou de `set_item_checked`, o servidor relê `bookmarks.iped`, para não sobrescrever o que o IPED Desktop já salvou.
- **Testes de integração sem resíduo no caso:** `test_stdio_mcp.ps1` e `test_native_exe.ps1` usam um marcador de teste neutro e o excluem ao final, e os testes automatizados apagam os marcadores que criam.
- **`add_to_bookmark` e `set_item_checked`:** as descrições passam a exigir a confirmação do usuário antes de cada escrita e a proibir nomes de marcador que indiquem autoria de IA; sai a orientação de marcar itens por iniciativa própria. A etapa de registro do `start_case` muda da mesma forma.
- **Versão declarada só no `pom.xml`:** o número da versão deixa de ser repetido no código, nos testes, nos scripts e na documentação. Para trocar a versão: `mvn versions:set "-DnewVersion=X.Y.Z" "-DgenerateBackupPoms=false"`. Uma build sem a filtragem de recursos do Maven informa a versão `dev`, e a data de build e a versão do IPED como `desconhecida`, em vez de um número de release. Os testes (`VersionInfoTest`, `test_stdio_mcp.ps1`, `test_native_exe.ps1`) comparam a versão informada com a do pom e reprovam `dev`. A wiki usa `<versão>` nos nomes de arquivos, e o badge do README mostra a última tag `vX.Y.Z`.
- **BREAKING — scripts de empacotamento:** `package_app.ps1` e `package_msi.ps1` não aceitam mais o parâmetro `-Version` nem assumem uma versão de reserva; leem a versão do `pom.xml` e param com erro se não conseguirem. O `package_app.ps1` passa a empacotar só `target/iped-tools-mcp-<versão>-runner.jar` da versão do pom e para com erro se ele não existir, em vez de usar o jar de outra versão que estiver em `target/`.
- **Wiki e README:** o `start_case` é acionado pelo menu de prompts do cliente (no Claude Code, `/iped:start_case`); digitado como texto comum, ele não é acionado.
- **Wiki reorganizada:**
  - **Menu:** passa a começar pelo grupo **Instalação e Configuração** (Requisitos, Download, Instalação, Configurador Gráfico e guias dos clientes), com o próximo passo para a investigação. A página de instalação e cada guia de cliente terminam com o link do próximo passo.
  - **Página "Como Investigar com o MCP":** substitui "Fluxo de Investigação" e traz um passo a passo:
    - iniciar o `start_case` em cada cliente, incluindo o **Copiar prompt** no LM Studio e nos clientes sem prompts MCP;
    - informar a pasta do caso;
    - responder ao alerta de conteúdo sensível;
    - exemplos de perguntas;
    - registrar marcadores e revisar no IPED Desktop.

    Depois do passo a passo vêm a metodologia e a personalização. A página antiga virou um aviso com o link.
  - **Contagem de ferramentas:** o total aparece só no Catálogo.
- **BREAKING — `get_communications_graph`:** o nó sintético `Dispositivo_Local` foi removido; o lado local de cada evento com direção explícita passa a ser o nó `local:<canal>` (`type: LOCAL_ENDPOINT`, `identity_resolved: false`) com os identificadores locais observados. Os nós passam a ter `type` (`ACCOUNT`, `GROUP`, `LOCAL_ENDPOINT`) e os ids são chaves canônicas (ex.: `tel:15550101181`).
- **BREAKING — `channels`:** passa a conter o aplicativo real (ex.: `Viber`) em vez de rótulos genéricos (`chat messages`, `calls`).
- **BREAKING — `get_document_metadata`:** os blocos `basic`, `communication`, `geo`, `forensic` e `extra` dão lugar aos grupos do catálogo (`Basic`, `Communication`, `AI`, `Image`, `UFED`...). A lista de permissões manual foi removida: `nudityScore`, `nudityClass`, `ocrCharCount`, `Regex:*`, `language:*`, `pdf:*` e `office:*` deixam de ser descartados no modo padrão.
- **BREAKING — ferramentas removidas (issue #2):** `list_ai_filters`, `query_ai_detections`, `get_property_dictionary` e `list_available_properties`. Consultavam propriedades que o IPED não grava (`hasWeapon`, `hasDrug`, `isNudity`, `hasFace`, `hasOCR`), usavam a escala errada de `nudityScore`, listavam nomes divergentes (`Message-AttachmentCount` em vez de `Message:AttachmentCount`) e amostravam só os 100 primeiros itens. O servidor passa a expor 26 ferramentas.

  | Ferramenta removida | Como fazer agora |
  |---|---|
  | `list_ai_filters` | `list_properties(group="AI")` |
  | `query_ai_detections` | `describe_property` para a escala e `search_documents` com a propriedade (ex.: `nudityScore:[650 TO *]`, `faceAge\:labels:child`, `ufed\:mediaClasses:Weapons`) |
  | `get_property_dictionary` | `list_properties(group=...)` ou `list_properties(pattern=...)` e `describe_property(name=...)` |
  | `list_available_properties` | `list_properties(category=...)`, com contagens exatas sobre todos os itens da categoria |
- **BREAKING — `get_device_and_owner_info` substituída por `get_device_and_accounts` (issue #3):** a ferramenta devolve só identificadores registrados pela extração e pelos parsers do IPED, cada um com a origem (item, app, propriedade e tipo de fonte) e a quantidade de apps e itens que o sustentam. São eles: entradas de informações do aparelho (IMEI, IMSI, ICCID, MSISDN, modelo), telefones, e-mails, IDs de mensageiro e usernames das contas de usuário, e perfis de usuário e hives do registro em computadores. Nenhum valor é rotulado como do dono, e mídias sem identificadores estruturados recebem resposta explícita com sugestões. O total de ferramentas continua 26.

  | Campo removido | Onde está agora |
  |---|---|
  | `likely_owners`, `likely_owner_names` | Não há substituto: o servidor não aponta o dono. Compare `account_identifiers` (ordenados por `app_count`) e cite os `item_id`. |
  | `owner_phone_numbers`, `owner_emails` | `account_identifiers.phone` e `account_identifiers.email`, com as fontes de cada valor |
  | `primary_accounts`, `user_accounts` | `sources` de cada identificador (item, app e propriedade) |
  | `device_properties` | `device_information` (`name`, `value`, `item_id`) |
  | `user_profile_dirs` | `user_profiles` (com `item_id`) |
  | `system_info` (`registered_owner`, `computer_name`...) | Removido: o IPED não grava essas propriedades. Leia os hives listados em `registry_hives` com `get_document_text`. |
- **`list_properties` com o filtro `identity`:** a semântica curada marca as propriedades de identificação (`phoneNumber`, `userAccount`, `userName`, `emailAddress`, `ufed:PhoneNumber`, `ufed:Username`, `ufed:UserID`, `ufed:EmailAddress`, `ufed:EntryName`, `ufed:EntryValue`); cada propriedade listada informa a marca, e `list_properties(identity=true)` lista só essas.
- **`search_documents` e prompt `start_case`:** deixam de listar nomes fixos de categorias e filtros de IA e orientam a descoberta por `list_categories`, `list_properties` e `describe_property`.
- **`get_top_contacts` e `get_communications_graph` não usam mais `get_device_and_owner_info`, `ufed:PhoneOwner` nem `Conversation:Account`:** nenhuma identidade é rotulada como dono do aparelho.

### Removido
- **Cursor deixa de ser cliente homologado:** nunca foi testado com o servidor. O guia `Cliente-Cursor` sai da wiki, e o Cursor sai do menu, da página inicial, de Requisitos, da tabela de "Como Investigar com o MCP" e do README. No configurador, a opção JSON passa a se chamar "Claude Desktop / Antigravity (JSON)", com o mesmo JSON de antes. A dica do formulário do LM Studio passa a indicar o "Copiar prompt", já que o LM Studio não lista prompts MCP.
- **BREAKING — argumentos `--version`, `-v`, `--help` e `-h`:** no executável instalado eles não mostravam nada no terminal, porque o lançador nativo do jpackage não tem console e executa a JVM como processo filho. O executável passa a reconhecer só `--stdio`; esses e quaisquer outros argumentos abrem o configurador, que mostra a versão no título e no "Sobre". A versão também é informada no `initialize` (`serverInfo.version`) e no `get_server_status` (`server_version`), e o teste STDIO do configurador a confere. A wiki valida a instalação pelo configurador, e os roteiros de teste conferem a versão pelo protocolo.
- **Sincronização do caso entre configurador e conversas:** o arquivo `~/.iped-tools-mcp/active_case.txt` não é mais lido nem gravado. Um arquivo deixado por versões anteriores é ignorado e pode ser apagado. A restauração do último caso ao abrir o configurador e o acompanhamento do caso aberto pelo chat também saem.

### Corrigido
- **Mensagens "Unrecognized format specifier" do log4j:** a cada início, dezenas dessas linhas poluíam o log do cliente de IA. Na montagem do pacote, o cache de plugins do `log4j-core` era sobrescrito pelo do IPED. O `log4j-core` foi substituído pela ponte `log4j2-jboss-logmanager`, e o log das classes do IPED passa a sair no mesmo formato e destino do resto. O executável também passa a definir `-Djava.util.logging.manager=org.jboss.logmanager.LogManager`, o que elimina o aviso `The LogManager accessed before "java.util.logging.manager"...`.
- **PNG, HEIC e SVG em `get_item_thumbnail` presos à miniatura de 256 px:**
  - **PNG:** o leitor de PNG do IPED não funcionava no Java 21. O lançador e os testes passam a liberar o acesso de que ele precisa (`--add-exports` de `java.desktop`).
  - **HEIC, SVG e outros formatos que o Java não lê:** passam a ser convertidos com o ImageMagick do caso, como o IPED faz, quando a conversão externa está ativa na configuração do caso. A imagem entra e sai por fluxo, sem gravar nada na pasta do caso.
- **`get_item_thumbnail` limitado à miniatura do IPED:** com a miniatura gravada no processamento (em geral 256 px), a ferramenta nunca passava desse tamanho, mesmo com `max_dimension` 2048, e documentos fotografados ficavam ilegíveis. Agora, quando o tamanho pedido é maior que a miniatura, a imagem vem do arquivo original, subamostrada como o IPED faz e com a orientação EXIF aplicada. A resposta informa a origem e o tamanho original.
- **Texto de OCR e transcrições ausentes em `get_document_text`:** imagens com OCR voltavam sem texto, e áudios devolviam cadeias binárias no lugar da transcrição, embora o IPED mostrasse os dois na aba "Texto". O MCP criava o parser antes de aplicar a configuração de parsing do caso, e a configuração ficava fixa pelo resto do processo. Agora a configuração do caso é aplicada antes, e a resposta começa pelas seções "Texto de OCR (IPED)" e "Transcrição de áudio (IPED)". O OCR é lido do cache do caso (`iped/text/ocr-results.db`) em modo somente leitura: o MCP não executa OCR nem grava na pasta do caso.
- **Abertura do caso travada por Python instalado na máquina:** com uma instalação de Python ou do IPED no ambiente (`PATH`, `PYTHONHOME`), a carga das configurações do IPED podia iniciar o Python embutido com bibliotecas de versões diferentes e travar. O cliente de IA ficava sem resposta e fechava a conexão. O MCP não precisa de Python e agora desativa o Python embutido do IPED antes de abrir o caso. Faces, similaridade, transcrições e demais resultados gravados no caso continuam disponíveis.
- **Falhas silenciosas ao abrir o caso ativo:** quando a abertura automática falhava, ou quando o caso ativo apontava para uma pasta inválida, o erro só era registrado em nível DEBUG e as ferramentas diziam apenas que não havia caso aberto. Agora a falha aparece no log e em `get_server_status`.
- **Gravação de marcadores e seleção que não chegava ao caso:** quando `bookmarks.iped` não podia ser gravado (por exemplo, caso em mídia somente leitura), o IPED desviava a gravação, sem avisar, para um arquivo na pasta temporária do usuário, e `add_to_bookmark` e `set_item_checked` respondiam sucesso. Agora a gravação vai para `bookmarks.iped` por um arquivo temporário na mesma pasta, e qualquer falha volta como erro, com o estado em memória restaurado a partir do arquivo.
- **Scripts de teste STDIO e do executável nativo:** num console com entrada em UTF-8 com BOM, o BOM era enviado antes da primeira mensagem JSON-RPC e o `initialize` falhava com erro de parse. Os scripts passam a usar UTF-8 sem BOM na entrada do processo.
- **Telefone do dono tirado de regex (issue #3):** a ferramenta de identificação do aparelho apontava como telefone do dono o primeiro valor de `Regex:PHONE` de arquivos de configuração e trazia valores sem sentido (IDs de usuário do Android, pastas de apps) como prováveis donos. Agora só contas estruturadas e informações do aparelho são fonte, e a conclusão sobre quem usa o aparelho fica com quem analisa.
- **Faixas em propriedades numéricas do processamento (issue #7):** consultas como `nudityScore:[500 TO 1000]`, `ocrCharCount:[1 TO *]` e `audio\:transcriptConfidence:[0.5 TO *]` retornavam 0. Os tipos de `metadataTypes.txt` agora são carregados antes da abertura do índice, e as faixas viram consultas numéricas. Limites decimais escritos com ponto (`0.5`) são convertidos para a localidade do caso. Quando uma faixa não encontra itens, `search_documents` devolve `warnings` dizendo se a propriedade não existe no caso, se é textual ou quais são seus valores mínimo e máximo.
- **Direção das comunicações (issue #4):** `"OUTGOING".contains("IN")` fazia toda mensagem enviada ser contada também como recebida. A direção agora vem somente de `Communication:Direction` ou `ufed:Direction` (comparação exata); chamadas UFED passam a ter direção, e eventos sem direção explícita ficam como `unknown` em vez de serem tratados como recebidos.

---

## [1.0.1] - 2026-10-10

### Adicionado
- **Prompt Forense MCP `start_case` (`ForensicPrompts`):**
  - Implementação do prompt pericial `start_case` estabelecendo o protocolo de engajamento entre o LLM e o caso IPED ativo.
  - Diretiva de *Dynamic Language Mirroring*: saudação inicial padronizada em português brasileiro, adaptando-se fluidamente ao idioma de comunicação do perito.
  - Salvaguardas periciais de conformidade forense: restrição estrita de leitura (read-only no filesystem), preservação de cadeia de custódia e orientação ativa para registro de evidências em marcadores (`add_to_bookmark`) e triagem (`set_item_checked`).
- **Extração de Metadados Brutos e Filtragem Cirúrgica (`DocumentMetadataTool`):**
  - Parâmetro `raw: true` em `get_document_metadata` para extração fidedigna de propriedades Lucene sem truncamento de strings (500 caracteres) ou de listas (10 itens).
  - Parâmetro `keys` com suporte a padrões glob/wildcards case-insensitive (ex: `Hardware-Wallet-*`, `ai:*`, `Communication:*`) para inspeção de campos críticos com economia de tokens de contexto.
- **Integração com Tarefas Python e JavaScript do IPED:**
  - Preservação e roteamento semântico de propriedades geradas por tarefas auxiliares do IPED (`SearchHardwareWallets.py`, `CSAMDetectorTask.py`, `AgeEstimationTask.py`, `NSFWNudityDetectTask.py`).
  - Novo domínio pericial `crypto` catalogando artefatos de hardware wallets (`Hardware-Wallet-Found`, `Hardware-Wallet-VendorName`, `Hardware-Wallet-DeviceName`, etc.).
  - Domínio pericial `ai` enriquecido com modelos de deep learning e redes neurais (`ai:csamDetector:csam`, `faceAge:count:Child`, `nsfw_nudity_score`).
  - Novos filtros em `query_ai_detections` e `list_ai_filters`: `crypto_wallets`, `age_estimation`, `nsfw` e busca híbrida de `csam` (hashes conhecidos + rede neural).
- **Descoberta Centralizada de Fontes de Evidência:**
  - Nova ferramenta MCP `list_sources` expondo as raízes do caso pericial e contêineres analisados.

### Modificado
- **Identificação Generalizada de Dispositivos (`get_device_and_owner_info`):**
  - Suporte híbrido transparente para extrações móveis (UFED/GrayKey) e imagens de computadores (.E01/raw/vmdk).
  - Descoberta e estruturação de múltiplos contêineres forenses sob a chave `evidences`, com classificação automática do caso (`mobile`, `computer` ou `hybrid`).
  - Extração profunda de números de telefone e e-mails utilizando padrões regex nativos do IPED (`Regex:PHONE`, `Regex:EMAIL`, `phone`, `cellPhone`).
- **Atualização do Ecossistema Quarkus:**
  - Atualização do Quarkus para a versão `3.39.3`.
- **Expansão da Cobertura de Testes Automatizados:**
  - Suíte de testes unitários expandida para 52 testes com 100% de aprovação em `IpedCoreServiceTest`, `McpToolsTest` e `VersionInfoTest`.

### Corrigido
- **Compatibilidade de Inicialização com Claude Desktop e Windows MSIX:**
  - Salvaguarda automática da propriedade `user.dir` no método `main` de `McpApplication`, redirecionando para `~/.iped-tools-mcp` quando o processo herda diretório restrito do sistema (`C:\Windows\System32`), prevenindo falhas fatais com `AccessDeniedException` ao acessar `System32\config`.
  - Inicialização assíncrona do leitor de casos IPED no modo `--stdio` via thread daemon (`iped-case-preloader`), liberando o loop de eventos STDIN do Quarkus em menos de 1,5 segundos para evitar estouro de timeout de conexão em clientes MCP.
  - Injeção da opção JVM `-Duser.dir=$APPDIR` no arquivo de configuração nativo `IPED-Tools-MCP.cfg` e script de empacotamento `package_app.ps1`.
- **Subsistema GUI no Executável Nativo Windows:**
  - Remoção da flag `--win-console` no `package_app.ps1` e `package_msi.ps1`, restaurando o subsistema `IMAGE_SUBSYSTEM_WINDOWS_GUI` e prevenindo a abertura de janela fantasma de terminal ao executar o aplicativo por duplo-clique.
  - Preservação dos descritores de stream `stdin`/`stdout` para comunicação JSON-RPC quando executado via clientes MCP.
- **Empacotamento e Permissões no Windows:**
  - Encerramento preventivo de instâncias ativas do `IPED-Tools-MCP.exe` antes da substituição de arquivos no `package_app.ps1`, evitando travamento de arquivos por bloqueio de DLLs no Windows.
  - Higienização pós-build desmarcando o atributo `ReadOnly` dos binários em `dist/` e purga da pasta temporária `target/dist-build`.
  - Correção na configuração do plugin Maven Surefire (`@{argLine}`) no `pom.xml`.

---

## [1.0.0] - 2026-09-29

### Adicionado
- **Arquitetura de Servidor MCP Nativo:**
  - Servidor construído sobre o ecossistema Quarkus 3.17.8 e Java 21 LTS de alta performance.
  - Implementação completa do protocolo **Model Context Protocol (MCP)** versão `2024-11-05` via transporte STDIO (JSON-RPC 2.0).
  - Leitor in-process direto sobre índices Apache Lucene e bancos SQLite do IPED (`IpedCoreService`), sem necessidade de servidor HTTP webapi intermediário.

- **Catálogo de 27 Ferramentas Periciais Forenses (MCP Tools):**
  - **Gestão de Casos e Conectividade:** `get_server_status`, `check_connection`, `get_case_summary`, `open_case` (com suporte a troca a quente de casos).
  - **Dicionário e Descoberta de Metadados:** `get_property_dictionary`, `list_available_properties`.
  - **Busca e Extração Forense:** `search_documents`, `get_document_metadata`, `get_document_text`, `list_categories`, `list_bookmarks`, `add_to_bookmark`.
  - **Inteligência de Dispositivos e Contatos:** `get_device_and_owner_info`, `get_top_contacts`, `get_communications_graph`.
  - **Análise Temporal e Cronológica:** `get_timeline`, `get_events_around_time`.
  - **Navegação e Estrutura de Evidências:** `list_folder_contents`, `get_item_relations`.
  - **Triagem Pericial:** `set_item_checked` (marcação de itens conferidos na evidência).
  - **Investigação Multimodal e Similaridade:** `get_item_thumbnail`, `search_similar_images` (pHash), `search_similar_faces` (reconhecimento facial), `search_similar_documents` (MoreLikeThis).
  - **Filtros e Detecções de IA:** `list_ai_filters`, `query_ai_detections`.

- **Interface Gráfica Integrada (Desktop Swing):**
  - Painel de controle `MainWindow` com indicação visual de status do servidor, caso ativo, uso de memória JVM e ferramentas carregadas.
  - Seletor de casos IPED com validação de pasta de índice.
  - Janela modal interativa "ℹ Sobre" apresentando detalhes de versão, build, links para [www.ipedtools.com.br](https://www.ipedtools.com.br) e repositório GitHub.

- **Infraestrutura de Versionamento Semântico:**
  - `pom.xml` consolidado como fonte única da verdade para a versão do produto (`1.0.0`).
  - Geração automática de `version.properties` durante o ciclo de build do Maven (`maven.build.timestamp`).
  - Classe utilitária `VersionInfo` provendo metadados de versão, data de compilação e versão do IPED Core.
  - Suporte completo às flags de linha de comando `--version` e `-v` com saída em texto limpo e encerramento imediato.
  - Exposição de `server_version` nas ferramentas de diagnóstico MCP e publicidade de versão no handshake `initialize`.

- **Pipeline de Empacotamento e Distribuição Automatizado:**
  - Script `scripts/package_app.ps1` com geração de runtime Java 21 enxuto via `jlink`, imagem nativa Windows `.exe` via `jpackage` e pacote portátil `.zip` (`IPED-Tools-MCP-1.0.0-windows-x64-portable.zip`).
  - Script `scripts/package_msi.ps1` para geração do instalador pericial Windows `.msi` (`IPED-Tools-MCP-1.0.0.msi`), com download automatizado do WiX Toolset 3.11 sob demanda.
  - Geração automática de manifesto criptográfico de integridade forense `dist/SHA256SUMS.txt`.

- **Governança do Repositório e Garantias Forenses:**
  - Arquivo `.gitignore` robusto protegendo contra commit acidental de evidências periciais (`*.iped`, `active_case.txt`, bancos locais) e binários pesados (`dist/`, `target/`, `tools/`).
  - `README.md` detalhado com arquitetura, catálogo de ferramentas e guia de uso com Claude Desktop.
  - `CONTRIBUTING.md` formalizando o modelo GitFlow tradicional, regras SemVer e fluxo de PRs.
  - Bateria de testes de integração automatizados (`scripts/test_stdio_mcp.ps1` com 25 testes do protocolo e `scripts/test_native_exe.ps1` para o binário nativo).

[Unreleased]: https://github.com/thiagofuer/iped-tools-mcp/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/thiagofuer/iped-tools-mcp/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/thiagofuer/iped-tools-mcp/releases/tag/v1.0.0
