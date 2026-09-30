# Design: Communications Network Graph and Contact Analytics

## Context
See `proposal.md` for motivation. IPED decodes communications into structured categories (`chat messages`, `calls`, `emails`) with standardized fields (`Communication:From`, `Communication:To`, `Communication:Direction`, `Communication:Date`).

## Goals / Non-Goals

**Goals:**
- Aggregate communication channels to rank top interlocutors.
- Return structured graph nodes and edges for network analysis.
- Filter out noise (e.g. one-off SMS verification codes).

**Non-Goals:**
- Rendering interactive 2D physics visualizers inside STDIO (the tool outputs clean JSON graph representations for LLM cognitive analysis and chat rendering).

## Decisions

### Decision 1: Aggregation over Normalized Communication Fields
- **Choice:** Collect items matching `category:("chat messages" OR "calls" OR "emails")` and extract from/to identities.
- **Rationale:** Normalizes disparate apps (WhatsApp, Telegram, Phone calls, Gmail) into a single cohesive network model.

### Decision 2: Graph Node and Edge Representation
- **Choice:** Format output as:
  - `nodes`: `id`, `name`, `phone_or_account`, `interaction_count`
  - `edges`: `source`, `target`, `weight` (message/call count), `channels`, `first_interaction`, `last_interaction`
- **Rationale:** Compatible with standard graph analysis algorithms and easily interpreted by LLMs.

### Decision 3: Configurable Interaction Threshold
- **Choice:** Default `min_interactions` to 5 to automatically filter out automated OTPs, spam messages, and incidental contacts.

## Risks / Trade-offs

- **[Risk] Large cases generating thousands of edges**  
  → *Mitigation:* Cap returned edges to a default limit (e.g. 50), prioritized by edge weight (highest volume first).
