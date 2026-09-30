# Design: Metadata Dictionary and Structured Forensic Properties

## Context
See `proposal.md` for motivation. Currently, `IpedCoreService.java` relies on `ALLOWED_METADATA_KEYS` containing approximately 20 hardcoded field names. Meanwhile, IPED's core architecture (`BasicProps.java`, `ExtraProperties.java`, and `iped-parsers-impl`) extracts over 100 domain-specific attributes for chats, web browsing, emails, media, system logs, and Cellebrite UFED extractions.

## Goals / Non-Goals

**Goals:**
- Replace the rigid 20-field whitelist with an intelligent prefix-based semantic filter that preserves all forensic attributes while omitting Lucene internal engine artifacts.
- Implement an embedded, domain-curated forensic dictionary accessible via `get_property_dictionary`.
- Implement `list_available_properties` to discover fields populated in specific categories of the active case.
- Reorganize `get_document_metadata` output into clean semantic blocks (`basic`, `communication`, `geo`, `forensic`, `extra`).

**Non-Goals:**
- Modifying or re-indexing IPED cases on disk (strict read-only constraint).
- Dumping raw embedding vectors or internal binary decompressor buffers into the LLM context.

## Decisions

### Decision 1: Curated Domain Dictionary with Live Category Inspection
- **Choice:** Provide an embedded dictionary of known IPED properties grouped by domain (`chats`, `browsers`, `emails`, `media`, `system`, `gps`, `ufed`, `ai`), coupled with a live tool (`list_available_properties`) that queries the Lucene index for populated fields in a given category.
- **Alternatives Considered:**
  - *Full dynamic schema scan on startup:* Scanning all Lucene field names across 300,000+ documents on startup adds unnecessary load time and lacks human-friendly field descriptions and Lucene escaping hints.
  - *No dictionary tool, only metadata expansion:* Without a dictionary tool, the LLM cannot discover what fields exist to formulate specialized Lucene queries.

### Decision 2: Structured Grouping in `get_document_metadata`
- **Choice:** Partition properties into logical sections:
  - `basic`: `name`, `path`, `category`, `type`, `size`, `created`, `modified`.
  - `communication`: `Communication:Direction`, `Communication:From`, `Communication:To`, `Message-Subject`, `GroupID`, etc.
  - `geo`: `common:geo:locations`, coordinates, place names.
  - `forensic`: `hash`, `md5`, `sha-256`, `deleted`, `carved`, `source`.
  - `extra`: Application-specific attributes (UFED, browser downloads, EXIF).
- **Alternatives Considered:**
  - *Single flat map:* Harder for LLMs to distinguish high-priority communication or location data from generic file attributes.

### Decision 3: Prefix-based Whitelist and Internal Noise Suppression
- **Choice:** Whitelist standard prefixes (`Communication:`, `Conversation:`, `common:`, `image:`, `video:`, `audio:`, `ufed:`, `p2p:`, `hashDb:`, `meta:`) and common user/account tokens, while explicitly blacklisting Lucene internal index fields (e.g. `_stored`, internal offsets, raw vectors).
- **Rationale:** Ensures future IPED parsers following standard naming conventions are automatically supported without requiring manual code changes.

## Risks / Trade-offs

- **[Risk] Increased token consumption for documents with numerous metadata fields**  
  → *Mitigation:* Cap multi-value field representations to a maximum of 10 entries and truncate exceptionally long metadata strings (> 500 chars).
- **[Risk] Syntax errors in Lucene queries due to colon (`:`) in field names like `Communication:From`**  
  → *Mitigation:* `get_property_dictionary` explicitly documents and provides query examples with necessary backslash escapes (`Communication\:From`).
