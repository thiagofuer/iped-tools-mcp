# Metadata Dictionary Specification

## Purpose
Provides structured field discovery and schema inspection tools for IPED indexed evidence, enabling LLMs to understand domain-specific properties and formulate high-precision Lucene queries.

## Requirements

### Requirement: Forensic Property Dictionary
The server SHALL expose `get_property_dictionary` returning searchable field names, descriptions, data types, and query syntax examples grouped by forensic domain (`chats`, `browsers`, `emails`, `media`, `system`, `gps`, `ufed`, `ai`).

#### Scenario: LLM requests property dictionary for chats
- **WHEN** the LLM calls `get_property_dictionary` with domain "chats"
- **THEN** the server returns field definitions for `Communication:Direction`, `Communication:From`, `Communication:To`, `Message-Body`, `GroupID`, and Lucene query examples with required escape characters.

#### Scenario: LLM requests complete property dictionary
- **WHEN** the LLM calls `get_property_dictionary` without specifying a domain
- **THEN** the server returns the dictionary encompassing all supported forensic domains.

### Requirement: Available Properties by Category
The server SHALL expose `list_available_properties` returning the distinct metadata property keys actually populated in the active case for a specified evidence category.

#### Scenario: LLM checks which fields exist in browsers history
- **WHEN** the LLM calls `list_available_properties` for category "browsers/history"
- **THEN** the server returns a list of distinct populated field names (e.g. `url`, `visitDate`, `Search`) present in the active case.
