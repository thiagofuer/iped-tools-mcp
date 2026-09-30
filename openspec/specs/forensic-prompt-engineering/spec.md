# Forensic Prompt Engineering Specification

## Purpose
Establishes standardized prompt engineering contracts across all forensic MCP tools, guaranteeing that tool annotations include explicit workflow guidance, negative constraints, query syntax rules, and domain value catalogs to prevent model hallucinations during investigations.

## Requirements

### Requirement: Mandatory Forensic Workflow Guidance and Negative Constraints
Every exposed forensic MCP tool SHALL declare actionable workflow instructions and explicit negative constraints within its `@Tool` description to prevent Large Language Models from executing hallucinated queries or inefficient brute-force scans.

#### Scenario: Examiner asks about target device owner
- **WHEN** an LLM evaluates how to determine the device owner
- **THEN** the `get_device_and_owner_info` tool description directs the model to call it first and explicitly forbids free-text Lucene queries for words like "proprietário" or "dono".

#### Scenario: Examiner begins a new investigation session
- **WHEN** an LLM initializes or inspects available tools
- **THEN** `get_server_status` and `list_sources` instruct the model to call them prior to any item-level queries to verify active case availability and retrieve the authoritative `source_id`.

### Requirement: Structured Query Syntax and Value Catalogs in Tool Schemas
Forensic tools accepting free-form or filtered inputs SHALL provide concrete query syntax examples, required character escaping rules, and exhaustive catalogs of supported categories or filter types in their tool annotations.

#### Scenario: LLM prepares a category search or AI filter query
- **WHEN** an LLM prepares a search query using `search_documents` or `query_ai_detections`
- **THEN** the tool parameter descriptions enumerate valid category names (e.g. `chat messages`, `emails`, `user accounts`) and valid AI detection types (`weapons`, `drugs`, `nudity`, `faces`, `audio_transcripts`).

#### Scenario: LLM prepares a Lucene query containing special characters
- **WHEN** an LLM inspects `search_documents` or `get_property_dictionary`
- **THEN** the tool descriptions specify how Lucene special characters (such as slashes, colons, and quotes) must be escaped.
