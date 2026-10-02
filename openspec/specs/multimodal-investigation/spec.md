# Multimodal Investigation Specification

## Purpose
Provides multimodal visual evidence delivery, computer vision similarity search, and automated deep learning detection inspection for forensic investigators and multimodal AI assistants.

## Requirements

### Requirement: Item Thumbnail Delivery
The server SHALL expose `get_item_thumbnail` returning a scaled Base64 JPEG representation formatted as an MCP image content block.

#### Scenario: Multimodal LLM inspects a document or photograph
- **WHEN** the LLM calls `get_item_thumbnail` with an item ID and optional `max_dimension`
- **THEN** the server returns an image content block with MIME type `image/jpeg` containing the scaled visual preview.

#### Scenario: Item is a non-visual binary or missing thumbnail
- **WHEN** the LLM requests a thumbnail for an item that cannot be rendered visually
- **THEN** the server returns an informative error message without terminating the connection.

### Requirement: Image and Facial Similarity Search
The server SHALL expose `search_similar_images` and `search_similar_faces` to query evidence by perceptual visual hashing and facial recognition vectors.

#### Scenario: Examiner searches for occurrences of a suspect face
- **WHEN** the LLM calls `search_similar_faces` with a reference image ID containing a face
- **THEN** the server returns a list of matching items ranked by facial similarity score above the specified threshold.

#### Scenario: Examiner searches for duplicates or variations of a seized picture
- **WHEN** the LLM calls `search_similar_images` with a reference image ID
- **THEN** the server returns visually similar images found across the case with their respective similarity confidence scores.

### Requirement: Similar Document Search
The server SHALL expose `search_similar_documents` to locate text files with matching phrasing or structure using statistical similarity algorithms.

#### Scenario: Examiner finds related draft documents
- **WHEN** the LLM calls `search_similar_documents` with a document ID
- **THEN** the server returns documents with matching lexical content ranked by score.

### Requirement: Automated AI Detections
The server SHALL expose `list_ai_filters` and `query_ai_detections` to list and query pre-computed machine learning detections, computer vision models, and specialized task detectors (weapons, drugs, adult content/nudity, faces, audio transcripts, CSAM neural/hash hits, hardware crypto wallets, and age estimation).

#### Scenario: Examiner queries for detected firearms
- **WHEN** the LLM calls `query_ai_detections` with `filter_type="weapons"` and an optional `min_score`
- **THEN** the server returns matching evidence items flagged by IPED's weapon detection model.

#### Scenario: Examiner lists available AI models in the case
- **WHEN** the LLM calls `list_ai_filters`
- **THEN** the server returns the categories of AI models and specialized detectors executed during case processing along with item counts.

#### Scenario: Examiner queries CSAM detections combining neural networks and hash databases
- **WHEN** the LLM calls `query_ai_detections` with `filter_type="csam"` and an optional `min_score`
- **THEN** the server queries both known hash database matches (`childPornHashHits:[1 TO *] OR hashDb\:status:alert`) and neural network classification scores (`ai\:csamDetector\:csam:[min_score TO 1.0] OR ai\:csamDetector\:label:csam`).

#### Scenario: Examiner queries detected hardware crypto wallets
- **WHEN** the LLM calls `query_ai_detections` with `filter_type="crypto_wallets"`
- **THEN** the server queries items flagged with `Hardware-Wallet-Found:true` or tagged under the "Possible Hardware Wallets" bookmark.

#### Scenario: Examiner queries age estimation for child/minor face detections
- **WHEN** the LLM calls `query_ai_detections` with `filter_type="age_estimation"`
- **THEN** the server queries items flagged with child face detections (`faceAge\:count\:Child:[1 TO *]` or `faceAge\:labels:Child`).
