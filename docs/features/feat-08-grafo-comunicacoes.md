# FEAT-08: Análise de Vínculos e Grafos de Comunicações (Graph Analytics)

**Código:** FEAT-08  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`appGraphAnalytics`, `graphDock`, `FilterSelectedEdges`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

Em investigações de organizações criminosas, tráfico de drogas, corrupção e estelionato, o IPED Desktop oferece o painel de **Análise em Grafos (`AppGraphAnalytics`)**, que mapeia visualmente as redes de relacionamento entre pessoas:
* Quem conversa com quem?
* Quais interlocutores mantêm o maior volume de trocas de mensagens e ligações?
* Quem é o nó central ou intermediário entre duas pessoas que aparentemente não se falam diretamente?

Com a ferramenta de grafos no MCP, a LLM pode responder a perguntas complexas de inteligência investigativa sem precisar percorrer mensagens individuais uma a uma, analisando a topologia da rede de comunicações.

---

## 2. Engenharia e APIs Internas do IPED Core

O IPED processa contatos e mensagens extraindo os participantes remetente e destinatário (`from`, `to`, `participants`, `phone`, `callType`):

```java
// Agregação de interações no IPED
// Consulta aos artefatos de comunicação (WhatsApp, Telegram, SMS, Chamadas)
String query = "category:(\"chat messages\" OR \"calls\" OR \"emails\")";
IPEDSearcher searcher = new IPEDSearcher(ipedSource, query);
SearchResult result = searcher.search();

// Agrupamento por pares de interlocutores (A -> B)
// Construção de nós (Contacts) e arestas ponderadas (Edges com contagem e timestamps)
```

---

## 3. Especificação das Ferramentas MCP (`@Tool`)

### 3.1 `get_top_contacts`
```java
@Tool(name = "get_top_contacts",
      description = "Ranks the most frequent communicators/contacts in the case by total volume of calls, messages, and interactions.")
public List<ContactInteractionRank> getTopContacts(
    @ToolArg(name = "limit", description = "Number of top contacts to return (default 20).") Integer limit
)
```

### 3.2 `get_communications_graph`
```java
@Tool(name = "get_communications_graph",
      description = "Extracts the network graph (nodes and edges) of communications between individuals, including message counts and date spans.")
public GraphResponse getCommunicationsGraph(
    @ToolArg(name = "focal_contact", description = "Optional phone number or name to center the network around.") String focalContact,
    @ToolArg(name = "min_interactions", description = "Filter out connections with fewer than N interactions (default 5).") Integer minInteractions,
    @ToolArg(name = "limit_edges", description = "Maximum number of edges to return (default 50).") Integer limitEdges
)
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Perguntas-Gatilho do Usuário:**
  * *"Com quem o investigado mais conversou?"*
  * *"Existe algum contato em comum entre o suspeito A e o suspeito B?"*
  * *"Mapeie os principais interlocutores deste aparelho."*
* **Workflow Guidance:**
  * Para perguntas sobre quem é o cúmplice ou contato mais frequente, acionar `get_top_contacts` diretamente.
  * Para reconstruir organogramas e vínculos de grupos de WhatsApp ou redes de telefonemas, invocar `get_communications_graph`.
