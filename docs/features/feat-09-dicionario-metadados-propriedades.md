# FEAT-09: Dicionário Forense de Metadados e Propriedades Estruturadas

**Código:** FEAT-09  
**Status:** Especificado (SDD)  
**Módulos IPED de Referência:**  
* `iped-api/src/main/java/iped/properties/BasicProps.java`
* `iped-api/src/main/java/iped/properties/ExtraProperties.java`
* `iped-parsers/iped-parsers-impl/src/main/java/iped/parsers/`
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

### 1.1 O Desafio Atual no MCP
Atualmente, no IPED Tools MCP, a recuperação de metadados (`get_document_metadata`) utiliza uma lista estática e restrita de cerca de 20 propriedades (`ALLOWED_METADATA_KEYS` em `IpedCoreService.java`), descartando a grande maioria das ricas informações forenses que o IPED extrai.

Além disso, a LLM muitas vezes realiza buscas genéricas no campo textual `content` ou `name` porque **não tem visibilidade dos campos indexados específicos** gerados pelos decodificadores do IPED para cada tipo de artefato (mensagens, histórico de navegação, logs de eventos, geolocalização, dados de UFED, metadados de câmera EXIF, etc.).

### 1.2 O Ecossistema de Propriedades do IPED
O IPED não é apenas um indexador de texto livre; ele é um **extrator forense semântico**. Através de dezenas de parsers especializados no pacote `iped-parsers-impl`, o IPED normaliza dados brutos em campos fortemente estruturados definidos em `BasicProps.java` e `ExtraProperties.java`.

---

## 2. Análise dos Parsers e Mapeamento de Propriedades

A análise detalhada do código-fonte em `iped-parsers/iped-parsers-impl/src/main/java/iped/parsers/` revela como cada domínio de evidência é estruturado:

### 2.1 Mensagens Instantâneas e Redes Sociais (`whatsapp`, `telegram`, `discord`, `skype`, `threema`)
Os parsers decodificam bancos de dados SQLite (como `msgstore.db` do WhatsApp, `tg.db` do Telegram) e arquivos de sessão, populando:
* **Sentido e Interlocutores:**
  * `Communication:Direction`: Sentido da mensagem (`Incoming` / `Outgoing`).
  * `Communication:From`: Identificador ou número do remetente (ex: `+5561999998888`).
  * `Communication:To`: Destinatário direto ou JID do grupo.
  * `Communication:Participants`: Lista de participantes em grupos.
  * `Communication:Date`: Data/hora exata do envio/recebimento com fuso horário.
* **Corpo e Anexos:**
  * `Message-Body`: Conteúdo textual da mensagem.
  * `Message-IsEmailAttachment`: Booleano indicando anexo de mídia.
  * `GroupID`, `isGroupMessage`: Identificador e flag de conversas em grupo.
* **Conversas e Sessões:**
  * `Conversation:id`, `Conversation:Name`, `Conversation:Type`, `Conversation:messagesCount`.
  * `Conversation:isOwnerAdmin`, `Conversation:Admins`.
* **Geolocalização em Mensagens:**
  * Coordenadas de locais compartilhados em conversas mapeadas em `common:geo:locations`.

### 2.2 Correio Eletrônico (`mail`)
Parsers de PST, OST, EML, MSG e MBOX:
* `Message-Subject`: Assunto da mensagem.
* `Communication:From`, `Communication:To`: Remetente e destinatários.
* `Message.MESSAGE_CC`, `Message.MESSAGE_BCC`: Cópias e cópias ocultas.
* `Message-IsEmailAttachment`, `Message-AttachmentCount`: Controle de anexos.
* Cabeçalhos técnicos de roteamento de e-mail (Message-ID, In-Reply-To).

### 2.3 Navegação na Internet e Downloads (`browsers`)
Parsers de Chrome, Firefox, Edge, Safari e Opera:
* **Histórico e Pesquisas:**
  * `url`: URL visitada pelo usuário.
  * `visitDate`: Data e hora exata da visita.
  * `Search`: Termos pesquisados no Google, Bing, DuckDuckGo etc.
* **Downloads:**
  * `downloadDate`: Data e hora de conclusão do download.
  * `localPath`: Caminho do arquivo gravado no disco rígido.
  * `totalBytes`, `receivedBytes`: Tamanho e bytes recebidos do download.

### 2.4 Multimídia, Imagens e Vídeos (`image`, `video`, `ocr`)
Parsers de EXIF, IPTC, XMP, ffmpeg e Tesseract OCR:
* **Fotografia e Câmera:**
  * `image:make`, `image:model`: Fabricante e modelo da câmera/celular que tirou a foto.
  * `image:exifDate`: Data e hora gravada no hardware da câmera.
  * `image:software`: Aplicativo utilizado para salvar ou editar a imagem.
* **Geolocalização (GPS):**
  * `common:geo:locations`: Coordenadas de latitude/longitude gravadas na foto.
* **Vídeo e Áudio:**
  * `video:duration`, `video:codec`, `video:frameRate`.
  * `audio:transcription`: Texto transcrito automaticamente (Whisper / VOSK).
  * `audio:transcriptConfidence`: Score de confiabilidade da transcrição.

### 2.5 Artefatos de Sistema Operacional e Execução (`evtx`, `registry`, `lnk`, `usnjrnl`, `misc`)
* **Logs do Windows (`evtx`):**
  * `EventID`: Identificador do evento (4624 logon, 4625 falha, 7045 novo serviço).
  * `ComputerName`, `SecurityID`, `LogonType`.
* **Registro do Windows (`registry`):**
  * `USBSTOR`: Dispositivos USB conectados (Fabricante, Modelo, Número de Série).
  * `UserAssist`: Programas executados com contagem de inicializações.
  * `RecentDocs`, `OpenSavePidlMRU`: Arquivos recentes acessados pelo usuário.
* **Atalhos (`lnk`):**
  * Caminho alvo original, volume serial e data do arquivo original.

### 2.6 Extrações Móveis UFED (`ufed`)
* `ufed:id`, `ufed:file_id`, `ufed:coordinate_id`, `ufed:sourceModels`.
* Preserva os modelos de entidade originais do Cellebrite Physical/Logical Analyzer.

---

## 3. Especificação das Novas Ferramentas MCP (`@Tool`)

Para que o LLM possa explorar essa riqueza de informações, propõem-se duas novas ferramentas e a reformulação da sanitização de metadados:

### 3.1 Ferramenta `get_property_dictionary`
Permite que o LLM consulte o catálogo de campos pesquisáveis e seus significados forenses:

```java
@Tool(name = "get_property_dictionary",
      description = "Returns the forensic metadata dictionary of indexed searchable fields in IPED, organized by domain (chats, web, emails, system, media, gps, ufed). Call this tool to discover exact Lucene field names before formulating specialized queries.")
public PropertyDictionaryResponse getPropertyDictionary(
    @ToolArg(name = "domain", description = "Optional filter domain: 'chats', 'browsers', 'emails', 'media', 'system', 'gps', 'ufed', 'ai'.", required = false)
    String domain
)
```

### 3.2 Ferramenta `list_available_properties`
Retorna as propriedades que efetivamente possuem valores no caso atual para uma determinada categoria:

```java
@Tool(name = "list_available_properties",
      description = "Lists all field names actually populated in the currently active case for a specific category (e.g. 'chat messages', 'browsers/history').")
public List<String> listAvailableProperties(
    @ToolArg(name = "category", description = "The target category to inspect.") String category
)
```

### 3.3 Aprimoramento da Ferramenta `get_document_metadata`
Em vez de descartar campos fora de uma lista rígida de 20 chaves:
1. **Preservação Semântica:** Manter todas as propriedades que iniciam com prefixos forenses conhecidos (`Communication:`, `Conversation:`, `common:`, `image:`, `video:`, `audio:`, `ufed:`, `p2p:`, `hashDb:`).
2. **Filtragem Inteligente de Ruído:** Omitir apenas campos puramente internos de motor Lucene ou buffers binários gigantescos (como vetores de embeddings crus ou offsets de descompressão).
3. **Agrupamento Estruturado no Retorno:** Organizar o JSON retornado em seções lógicas:
   * `basic`: `name`, `path`, `category`, `size`, `created`, `modified`.
   * `communication`: remetente, destinatário, sentido, data da mensagem, participantes.
   * `geo`: latitude, longitude, endereço formatado (se houver).
   * `forensic`: hashes (MD5, SHA-256), deleted, carved, source.
   * `extra`: propriedades específicas do parser Tika/IPED.

---

## 4. Impacto no Desempenho e Precisão da IA

| Pergunta do Usuário | Como a LLM buscava antes | Como a LLM buscará com o Dicionário |
|---|---|---|
| *"Busque mensagens recebidas pelo suspeito no WhatsApp do número 11988887777"* | `content:11988887777` (retorna milhares de logs e falsos positivos) | `Communication\:Direction:Incoming AND Communication\:From:*11988887777*` (precisão de 100%) |
| *"Quais sites o usuário acessou no dia 20?"* | `name:*.html AND date:2026-03-20` (incompleto) | `category:"browsers/history" AND visitDate:[2026-03-20 TO 2026-03-20T23:59:59]` |
| *"Existem fotos tiradas com iPhone?"* | `content:iPhone` (acha textos, emails, etc.) | `image\:make:Apple OR image\:model:*iPhone*` |
| *"Quais pendrives foram plugados no computador?"* | Inviável por busca de texto | `category:"system/registry" AND path:*USBSTOR*` |

---

## 5. Critérios de Aceite e Testes

1. A chamada `get_property_dictionary` deve retornar descrições claras, tipos de dados e exemplos de queries Lucene com escapes apropriados (ex: `Communication\:From`).
2. A ferramenta `get_document_metadata` deve retornar os campos de `Communication:*` e `common:geo:locations` para mensagens de WhatsApp e fotos com GPS sem cortá-los.
3. Não deve haver vazamento de ruídos binários ou listas cruas de milhares de inteiros internos do Lucene que sobrecarreguem o contexto de tokens da LLM.
