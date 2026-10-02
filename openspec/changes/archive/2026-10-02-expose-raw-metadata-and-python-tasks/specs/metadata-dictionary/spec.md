# Spec Delta

## MODIFIED Requirements

### Requirement: Forensic Property Dictionary
The server SHALL expose `get_property_dictionary` returning searchable field names, descriptions, data types, and query syntax examples grouped by forensic domain (`chats`, `browsers`, `emails`, `media`, `system`, `gps`, `ufed`, `ai`, `crypto`).

#### Scenario: LLM requests property dictionary for chats
- **WHEN** the LLM calls `get_property_dictionary` with domain "chats"
- **THEN** the server returns field definitions for `Communication:Direction`, `Communication:From`, `Communication:To`, `Message-Body`, `GroupID`, and Lucene query examples with required escape characters.

#### Scenario: LLM requests complete property dictionary
- **WHEN** the LLM calls `get_property_dictionary` without specifying a domain
- **THEN** the server returns the dictionary encompassing all supported forensic domains, including `ai` and `crypto`.

#### Scenario: LLM requests property dictionary for AI and machine learning models
- **WHEN** the LLM calls `get_property_dictionary` with domain "ai"
- **THEN** the server returns definitions and escaped query examples for deep learning fields including neural CSAM detection (`ai\:csamDetector\:csam`, `ai\:csamDetector\:label`), age estimation (`faceAge\:labels`, `faceAge\:count\:Child`), and Python NSFW detection (`nsfw_nudity_score`).

#### Scenario: LLM requests property dictionary for cryptocurrency and hardware wallets
- **WHEN** the LLM calls `get_property_dictionary` with domain "crypto"
- **THEN** the server returns field definitions and query syntax for hardware wallet artifacts (`Hardware-Wallet-Found`, `Hardware-Wallet-VendorName`, `Hardware-Wallet-DeviceName`).
