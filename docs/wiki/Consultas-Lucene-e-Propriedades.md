# Consultas Lucene e Catálogo de Propriedades

O IPED indexa todos os dados das evidências através do motor de busca **Apache Lucene 9.x**. A ferramenta `search_documents` aceita consultas diretas na sintaxe oficial do Lucene.

---

## 🔍 Sintaxe de Busca Apache Lucene

### 1. Termos Simples e Frases Exatas
* **Termo isolado:** `contrato` (localiza o termo em qualquer campo de texto indexado).
* **Frase exata:** `"contrato de prestação de serviços"` (termos exatos na ordem indicada).

### 2. Operadores Booleanos (Maiúsculas Obrigatórias)
* **AND:** `fraude AND licitação` (ambos os termos devem estar presentes).
* **OR:** `dólar OR euro` (pelo menos um dos termos).
* **NOT / Proibição:** `pagamento NOT comprovante` (contém pagamento mas não comprovante).

### 3. Filtros por Campo (`campo:valor`)
O IPED armazena centenas de propriedades estruturadas. Para restringir a busca a um metadado específico:
* **Por categoria:** `category:"chat messages"`
* **Por nome de arquivo:** `name:*.pdf`
* **Por remetente de e-mail:** `from:suspeito@empresa.com`
* **Por hash:** `hash:451b7a9a*`

### 4. Consultas por Intervalo de Datas
Para campos temporais em formato ISO-8601:
* `date:[2024-01-01 TO 2024-06-30]`
* `created:[2024-05-01T00:00:00Z TO 2024-05-01T23:59:59Z]`

### 5. Faixas Numéricas em Propriedades do Processamento
Módulos do IPED gravam propriedades numéricas (tipadas em `<caso>/iped/conf/metadataTypes.txt`), que aceitam faixas `[mínimo TO máximo]` (`*` deixa um lado aberto):
* `nudityScore:[650 TO *]` — imagens com pontuação de nudez alta (escala de 0 a 1000, módulo DIE).
* `ocrCharCount:[1 TO *]` — itens com texto reconhecido por OCR.
* `audio\:transcriptConfidence:[0.5 TO *]` — transcrições de áudio com confiança a partir de 0,5.

**Separador decimal:** escreva sempre com ponto (`0.5`). O servidor converte para o formato da localidade do caso; em português, o IPED leria `0.5` como `5`.

**Quando a faixa não encontra nada,** a resposta de `search_documents` traz o campo `warnings`, que explica o motivo:
* `numeric_range_no_results` — a propriedade existe, mas nenhum item está na faixa. O aviso informa o tipo e os valores mínimo e máximo do caso, para ajustar a faixa.
* `property_not_in_case` — a propriedade não existe no caso (o módulo que a gera pode não ter sido executado). Zero aqui **não** significa ausência de evidência.
* `range_on_non_numeric_property` — a propriedade é textual neste caso e a faixa é comparada como texto.

### 6. Curingas (Wildcards)
* `?` — Substitui exatamente um caractere: `t?ste` (encontra *teste*, *taste*).
* `*` — Substitui zero ou mais caracteres: `prop*` (encontra *propina*, *proposta*, *propriedade*).

---

## ⚠️ Escape de Caracteres Especiais

O Lucene reserva os seguintes caracteres com função sintática especial:
```text
+ - && || ! ( ) { } [ ] ^ " ~ * ? : \ /
```

Se o seu termo de busca contiver qualquer um desses caracteres literalmente (por exemplo, buscando um número de telefone com traço, um e-mail com arroba ou um caminho de pasta), você deve **escapá-los com duas barras invertidas `\\`**:

* **Buscando telefone com traço:** `content:99999\\-1234`
* **Buscando expressão matemática ou código:** `content:1\\+1`
* **Buscando caminhos:** `path:\\/home\\/usuario\\/documentos`

---

## 📚 Catálogo de Propriedades do Caso

Cada caso tem um conjunto próprio de propriedades: depende da versão do IPED, dos módulos habilitados no processamento, da origem (UFDR, E01, pastas) e de scripts da instituição. Por isso o servidor não usa uma lista fixa: ele lê do índice do caso quais propriedades existem e quantos itens têm cada uma, e acrescenta a explicação curada das propriedades conhecidas do IPED (significado, escala, valores e exemplos de consulta).

### Fluxo de descoberta

```
list_properties()                          -> grupos do caso e quantas propriedades cada um tem
list_properties(group="AI")                -> propriedades do grupo, com tipo, contagem exata e exemplo de consulta
list_properties(pattern="faceAge*")        -> propriedades cujo nome casa com o curinga
describe_property(name="nudityScore")      -> significado, escala, mínimo/máximo ou valores mais frequentes
search_documents(query="nudityScore:[650 TO *]")
```

* `list_properties(category="Instant Messages")` restringe as contagens à categoria e omite as propriedades que não aparecem nela. Use os nomes exatos de `list_categories`.
* `describe_property(name="faceAge:labels", query="category:\"Other Images\"")` calcula a distribuição dos valores só sobre os itens da consulta.
* As contagens são exatas: consideram todos os itens do caso (ou da categoria), não uma amostra.

### Grupos

| Grupo | Conteúdo |
|---|---|
| `Basic` | Nome, caminho, categoria, tipo, tamanho, datas, hashes, apagado/recuperado. |
| `AI` | Resultados de módulos de IA: `nudityScore` e `nudityClass` (DIE), `nsfw_*`, `face_count` e `faceAge:*` (faces e idade estimada), `ai:csamDetector:*`, `ocrCharCount`, `audio:transcription` e `audio:transcriptConfidence`, `ufed:mediaClass*`. |
| `HashDB` | Correspondências em bases de hashes: `hashDb:*`, `childPornHashHits`, `photoDnaDb:*`. |
| `Communication` | `Communication:*`, `Conversation:*`, `Message-*` e `Message:*` (chats, e-mails, chamadas). |
| `Common` | Metadados comuns (`common:*`), incluindo `common:geo:locations`. |
| `Regex` | Ocorrências de expressões regulares (`Regex:PHONE`, `Regex:EMAIL`, `Regex:BR_CPF`...). |
| `Language` | Idiomas detectados (`language:*`). |
| `Audio`, `Image`, `Video`, `PDF`, `Office`, `HTML` | Metadados técnicos por tipo de arquivo (`audio:*`, `image:*`...). |
| `NER`, `P2P`, `UFED`, `WindowsEvt` | Entidades nomeadas, compartilhamento P2P, extrações Cellebrite e eventos do Windows. |
| `Other` | Demais propriedades gravadas pelos parsers e módulos. |

### Ausente não é o mesmo que zero

Quando uma propriedade não aparece no catálogo, ela **não existe no caso**: o módulo que a gera pode não ter sido executado (por exemplo, OCR desligado). `describe_property` responde `present: false` e sugere nomes parecidos. Isso é diferente de uma propriedade que existe mas não tem itens na faixa pedida. Do mesmo modo, `search_documents` devolve em `warnings` um aviso `property_not_in_case` quando a consulta cita uma propriedade que não existe; a parte válida da consulta continua funcionando.

### Exemplos de consultas do grupo AI

| Objetivo | Consulta |
|---|---|
| Imagens com nudez provável (escala 1 a 1000) | `nudityScore:[650 TO *]` |
| Rostos estimados como de criança | `faceAge\:labels:child` |
| Imagens com ao menos um rosto de criança | `faceAge\:count\:child:[1 TO *]` |
| Classificação do detector de CSAM ou correspondência em base de hashes | `ai\:csamDetector\:label:csam OR childPornHashHits:[1 TO *] OR hashDb\:status:alert` |
| Itens com texto reconhecido por OCR | `ocrCharCount:[1 TO *]` |
| Transcrições de áudio com confiança a partir de 0,5 | `audio\:transcriptConfidence:[0.5 TO *]` |
| Mídias classificadas como armas pelo Cellebrite | `ufed\:mediaClasses:Weapons` |

Antes de usar um exemplo, confirme com `list_properties(group="AI")` que a propriedade existe no caso e veja a escala com `describe_property`.

### Perguntas de exemplo

* *"Quais resultados de inteligência artificial existem neste caso?"* → `list_properties(group="AI")`
* *"Qual a faixa de valores de nudityScore neste caso?"* → `describe_property(name="nudityScore")`
* *"Quais faixas etárias foram estimadas nas imagens?"* → `describe_property(name="faceAge:labels", query="category:\"Other Images\"")`
* *"Existe propriedade de OCR neste caso?"* → `describe_property(name="ocrCharCount")`
