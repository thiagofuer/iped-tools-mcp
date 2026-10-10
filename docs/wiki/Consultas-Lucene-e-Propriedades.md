# Consultas Lucene e Dicionário de Propriedades

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

### 5. Curingas (Wildcards)
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

## 📚 Domínios do Dicionário de Metadados (`get_property_dictionary`)

Em vez de tentar adivinhar nomes de colunas, a ferramenta `get_property_dictionary(domain="...")` retorna os nomes canônicos e tipos das propriedades indexadas. Os domínios disponíveis são:

| Domínio | Escopo e Conteúdo |
|---|---|
| `chats` | Mensagens, remetentes, destinatários, status de entrega, grupos, anexos de mensageria (WhatsApp, Telegram, Signal, Teams). |
| `browsers` | Histórico de navegação, URLs visitadas, termos de busca, cookies, downloads e favoritos de navegadores web. |
| `emails` | Cabeçalhos MIME, remetentes (`from`), destinatários (`to`/`cc`/`bcc`), assunto (`subject`), mensagens e anexos. |
| `media` | Metadados EXIF de fotografias (câmera, abertura, ISO, timestamp original), codecs de vídeo, resolução e taxas de áudio. |
| `system` | Dados de sistema operacional, logs de eventos, chaves de registro, usuários do sistema, atributos de arquivo e timestamps do sistema de arquivos. |
| `gps` | Coordenadas de latitude, longitude, altitude, velocidade, precisão e endereço geocodificado de fotos e bancos de dados móveis. |
| `ufed` | Propriedades específicas de relatórios forenses Cellebrite/UFED, GrayKey e Oxygen Forensic. |
| `ai` | Pontuações e classificações de modelos de Inteligência Artificial e visão computacional (armas, drogas, nudez, faces, CSAM, transcrições). |
| `crypto` | Identificadores de carteiras de criptomoedas, carteiras de hardware (Ledger, Trezor) e artefatos de blockchain. |

---

## 🔎 Descoberta Dinâmica no Caso (`list_available_properties`)

Para saber quais propriedades foram de fato populadas para uma categoria específica do seu caso, invoque:

```json
list_available_properties(category="whatsapp")
```

A ferramenta retornará todos os nomes exatos de propriedades que contêm dados indexados para aquela categoria específica, garantindo buscas 100% assertivas.
