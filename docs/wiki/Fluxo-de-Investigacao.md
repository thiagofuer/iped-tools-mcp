# Metodologia e Fluxo de Investigação Forense

O **IPED Tools MCP** foi concebido sob os princípios da norma internacional de computação forense **ISO/IEC 27037** (Diretrizes para identificação, coleta, aquisição e preservação de evidências digitais).

O modelo de linguagem atua como um assistente cognitivo do examinador: ele realiza buscas, analisa metadados, correlaciona eventos no tempo e sugere linhas investigativas, enquanto **o perito mantém o controle metodológico e a decisão decisória final**.

---

## 🧭 Metodologia em 6 Etapas

Quando o prompt oficial `/start_case` é ativado, a IA é instruída a seguir o seguinte ciclo de trabalho:

```
[1. Status e Prontidão]  -->  [2. Reconhecimento de Fontes]  -->  [3. Dicionário de Metadados]
      get_server_status              list_sources / get_device_info          get_property_dictionary
             |
             v
[4. Busca Estruturada]   -->  [5. Inspeção Progressiva]      -->  [6. Registro e Auditoria]
      search_documents               get_metadata / get_text                 add_to_bookmark
      query_ai_detections            get_item_thumbnail                      set_item_checked
```

### 1. Verificação do Ambiente e Caso
A sessão sempre se inicia verificando se há um caso IPED carregado e pronto (`get_server_status`), seguido da análise volumétrica do acervo (`get_case_summary`).

### 2. Reconhecimento de Fontes de Evidência
O perito e a IA identificam as mídias periciadas (`list_sources`) e os dados de proprietário e contas vinculadas (`get_device_and_owner_info`), como números de telefone, IMEI e e-mails.

### 3. Consulta ao Dicionário de Metadados
Para evitar alucinações de campos e erros de sintaxe, a IA consulta o catálogo canônico de propriedades (`get_property_dictionary` ou `list_available_properties`) antes de formular consultas específicas sobre chats, EXIF ou sistemas de arquivos.

### 4. Busca Estruturada e Recuperação
Execução de pesquisas com sintaxe Apache Lucene (`search_documents`), filtros de redes neurais (`query_ai_detections`), correlação temporal (`get_timeline`, `get_events_around_time`) ou análise de vínculos interpessoais (`get_communications_graph`, `get_top_contacts`).

### 5. Inspeção Progressiva de Conteúdo
Para economizar tokens e evitar estouro de contexto:
* Primeiro, inspecionam-se os metadados forenses sumarizados (`get_document_metadata`).
* Se houver relevância, extrai-se o texto paginado (`get_document_text`).
* Para mídias visuais, obtém-se a miniatura gráfica em Base64 JPEG (`get_item_thumbnail`).

### 6. Marcação e Registro Pericial
Itens de valor probatório são associados a marcadores periciais (`add_to_bookmark`) ou sinalizados na triagem pericial (`set_item_checked`).

---

## 📜 O Prompt Oficial: `start_case`

O servidor disponibiliza através da especificação MCP o prompt canônico `start_case`.

### Como acionar no cliente:
* **No Claude Desktop ou Claude Code:** Digite `/start_case` ou selecione o prompt no menu de integrações.
* **No LM Studio ou Cursor:** Peça no chat:
  > *"Execute o prompt start_case com foco em 'fraude em notas fiscais'"*

### Parâmetros Opcionais:
* `investigation_target`: Tema ou foco específico da investigação (ex: *"mensagens entre o suspeito A e o suspeito B"*).
* `case_path`: Caminho absoluto opcional do caso, caso queira que a IA invoque `open_case` automaticamente.

### Espelhamento Dinâmico de Idioma (Dynamic Language Mirroring):
O prompt instrui a IA a iniciar respondendo em **Português do Brasil (pt-BR)** como idioma padrão, mas a acompanhar fluentemente qualquer outro idioma utilizado pelo examinador ao longo do diálogo, mantendo inalterados termos técnicos internacionais e hashes forenses.

---

## 🔍 Ciclo Fechado com o IPED Desktop

O trabalho realizado pela IA não fica isolado no chat:

1. A IA encontra uma evidência crítica e executa:
   ```json
   add_to_bookmark(item_ids=[10425], bookmark_name="Evidências Relevantes - Operação")
   set_item_checked(item_id=10425, checked=true)
   ```
2. O servidor salva o estado diretamente no arquivo `iped/bookmarks.iped` do caso.
3. Ao abrir o caso no **IPED Desktop oficial**, o perito verá a evidência imediatamente marcada na árvore de marcadores e com o checkbox de conferência selecionado.
4. O perito realiza a revisão humana, confere a cadeia de custódia e exporta o laudo oficial em HTML ou PDF com total segurança jurídica.
