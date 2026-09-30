# Catálogo de Features — Paridade com o Módulo de Análise do IPED

**Versão:** 1.0.0  
**Data:** 2026-09-27  
**Origem do Mapeamento:** [`iped.app.ui.AppMain`](file:///C:/Users/thiag/Downloads/workspace/IPED/iped-app/src/main/java/iped/app/ui/AppMain.java) e [`iped.app.ui.App`](file:///C:/Users/thiag/Downloads/workspace/IPED/iped-app/src/main/java/iped/app/ui/App.java)

---

## 1. Visão Geral

A interface gráfica de análise do IPED (conhecida como **IPED Desktop** ou **Módulo de Análise**) é a principal ferramenta utilizada por peritos criminais federais e estaduais no Brasil para navegar, filtrar, cruzar e auditar gigabytes ou terabytes de dados apreendidos em operações policiais.

O objetivo estratégico do **IPED Tools MCP** é alcançar **paridade analítica** com a interface gráfica, permitindo que uma LLM (local ou remota) tenha acesso programático às mesmas visões, filtros, relacionamentos e ferramentas investigativas que um perito humano tem em sua tela.

---

## 2. Mapa de Correspondência: IPED Desktop ⇄ Ferramentas MCP

| Visão / Painel no IPED Desktop (`App.java`) | Capacidade Analítica Humana | Proposta de Ferramenta MCP para o LLM | Documentação SDD |
|---|---|---|---|
| `subItemTable`, `parentItemTable`, `duplicatesTable`, `referencesTable`, `referencedByTable` | Inspecionar de onde veio um arquivo, ver anexos/conteúdo extraído, localizar duplicatas idênticas por MD5/SHA256 em outros dispositivos. | `get_item_relations`<br>`find_duplicates` | [FEAT-01](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-01-relacoes-forenses.md) |
| `SimilarImagesSearch`, `SimilarFacesSearch`, `SimilarDocumentSearch` | Achar fotos visualmente parecidas, encontrar todas as fotos com o mesmo rosto de um suspeito, achar minutas ou contratos semelhantes. | `search_similar_images`<br>`search_similar_faces`<br>`search_similar_documents` | [FEAT-02](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-02-similaridade-multimodal.md) |
| `aiFiltersTree`, `AIFiltersTreeListener` | Filtrar evidências por detecções automáticas de redes neurais do IPED (armas, drogas, nudez, pornografia, CSAM, transcrições). | `list_ai_filters`<br>`query_ai_detections` | [FEAT-03](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-03-filtros-ia.md) |
| `timelineListener`, eventos temporais | Compreender a cronologia de fatos em torno do momento do crime (mensagens, ligações, arquivos criados, histórico web). | `get_timeline`<br>`get_events_around_time` | [FEAT-04](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-04-linha-do-tempo.md) |
| `GalleryTable`, `ImageThumbTask`, `galleryGrayButton` | Ver fotos, miniaturas e páginas renderizadas de documentos. | `get_item_thumbnail`<br>`get_image_data` (Base64 multimodal) | [FEAT-05](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-05-miniaturas-multimodal.md) |
| `tree` (Árvore de Diretórios / Volumes) | Navegar na estrutura de pastas da evidência física (ex: `C:\Users\...\AppData\Local`). | `list_folder_contents`<br>`get_parent_folder` | [FEAT-06](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-06-arvore-evidencias.md) |
| `exportToZip`, checkbox checked, anotações de marcadores | Marcar evidências para laudo pericial (*checkbox*), incluir anotações técnicas e exportar arquivos originais. | `set_item_checked`<br>`set_item_comment`<br>`export_evidence_items` | [FEAT-07](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-07-exportacao-triagem.md) |
| `appGraphAnalytics` | Visualizar grafo de vínculos e redes de relacionamento entre interlocutores. | `get_communications_graph`<br>`get_contact_interactions` | [FEAT-08](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-08-grafo-comunicacoes.md) |
| `MetadataPanel`, `BasicProps`, `ExtraProperties`, `iped-parsers-impl` | Inspecionar metadados estruturados (WhatsApp, emails, browsers, GPS, EXIF, EVTX, UFED) com precisão cirúrgica sem perda de campos. | `get_property_dictionary`<br>`list_available_properties`<br>`get_document_metadata` (rico) | [FEAT-09](file:///C:/Users/thiag/Downloads/workspace/IPEDToolsMCP/docs/features/feat-09-dicionario-metadados-propriedades.md) |

---

## 3. Diretrizes de Especificação das Features

Cada documento de especificação funcional e arquitetural segue o padrão SDD:
1. **Contexto & Justificativa Forense**: Como e por que o perito usa essa ferramenta no IPED.
2. **APIs Internas do IPED Core**: Classes e métodos Java do IPED engine que suportam a funcionalidade.
3. **Contrato de Interface MCP (`@Tool`)**: Nome da ferramenta, parâmetros de entrada (JSON Schema) e estrutura do retorno.
4. **Prompt Engineering & Workflow Guidance**: Instruções contextualizadas para que a LLM saiba quando e como invocar a ferramenta sem alucinações.
5. **Critérios de Aceite e Testes**: Validações esperadas contra casos reais do IPED.
