# FEAT-04: Linha do Tempo Forense (Timeline) & Análise Cronológica Contextual

**Código:** FEAT-04  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`timelineListener`, `timelineButton`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

A reconstrução da linha do tempo dos acontecimentos é um dos objetivos mais críticos em qualquer perícia criminal:
* Quando ocorreu o homicídio, a invasão de sistema ou a transação bancária fraudulenta?
* O que o investigado estava fazendo nos 30 minutos anteriores e posteriores ao fato?
* Que mensagens foram trocadas, quais chamadas foram atendidas, que fotos foram capturadas e quais arquivos foram abertos ou deletados nesse intervalo específico?

No IPED Desktop, o perito aciona o botão **Timeline** para visualizar a agregação cronológica de todos os eventos. Com esta feature, a LLM poderá receber uma narrativa ordenada minuto a minuto de tudo o que ocorreu no dispositivo em uma janela temporal de interesse.

---

## 2. Engenharia e APIs Internas do IPED Core

O IPED indexa datas em campos padronizados Lucene (ISO-8601 ou timestamps numéricos):
* `date`: data primária do artefato (data da mensagem, data da ligação, data do documento).
* `created`, `modified`, `accessed`: carimbos de data do sistema de arquivos (MAC).
* `exifDate`: carimbo embutido na câmera para fotos/vídeos.

```java
// Query temporal no Lucene
String query = String.format("date:[%s TO %s]", startIsoDate, endIsoDate);
if (categoryFilter != null) {
    query += " AND category:\"" + categoryFilter + "\"";
}

// Ordenação estritamente cronológica ascendente
Sort sort = new Sort(new SortField("date", SortField.Type.LONG, false));
IPEDSearcher searcher = new IPEDSearcher(ipedSource, query);
searcher.setSort(sort);
SearchResult results = searcher.search();
```

---

## 3. Especificação das Ferramentas MCP (`@Tool`)

### 3.1 `get_timeline`
```java
@Tool(name = "get_timeline",
      description = "Retrieves forensic events in strict chronological order within a start and end datetime range (ISO-8601, e.g. '2026-03-15T20:00:00Z').")
public TimelineResponse getTimeline(
    @ToolArg(name = "start_date", description = "Start of the time window (ISO-8601 or YYYY-MM-DD).") String startDate,
    @ToolArg(name = "end_date", description = "End of the time window (ISO-8601 or YYYY-MM-DD).") String endDate,
    @ToolArg(name = "category", description = "Optional category filter (e.g. 'chat messages', 'calls', 'photos').") String category,
    @ToolArg(name = "limit", description = "Maximum number of events to return (default 50).") Integer limit
)
```

### 3.2 `get_events_around_time`
```java
@Tool(name = "get_events_around_time",
      description = "Investigates what happened in the device around a specific target datetime (within +/- N minutes). Ideal for reconstructing the moment of a crime.")
public TimelineResponse getEventsAroundTime(
    @ToolArg(name = "target_datetime", description = "The focal point datetime (ISO-8601, e.g. '2026-03-15T21:45:00').") String targetDatetime,
    @ToolArg(name = "window_minutes", description = "Minutes before and after to inspect (default 30).") Integer windowMinutes,
    @ToolArg(name = "limit", description = "Maximum events to return (default 40).") Integer limit
)
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Perguntas-Gatilho do Usuário:**
  * *"O que o suspeito fez no celular entre 21h e 23h da noite do dia 15?"*
  * *"Quais mensagens foram trocadas logo após o horário da transferência bancária?"*
  * *"Reconstrua a sequência de eventos do dia do fato."*
* **Workflow Guidance:**
  * Sempre que o perito mencionar uma data/hora específica de um crime, invocar `get_events_around_time` para identificar as atividades imediatas ao redor do fato antes de realizar buscas genéricas.
