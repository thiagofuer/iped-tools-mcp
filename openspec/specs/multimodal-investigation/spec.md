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
The server SHALL expose `list_ai_filters` and `query_ai_detections` to list and query pre-computed machine learning detections (weapons, drugs, adult content, faces, and audio transcripts).

#### Scenario: Examiner queries for detected firearms
- **WHEN** the LLM calls `query_ai_detections` with `filter_type="weapons"` and an optional `min_score`
- **THEN** the server returns matching evidence items flagged by IPED's weapon detection model.

#### Scenario: Examiner lists available AI models in the case
- **WHEN** the LLM calls `list_ai_filters`
- **THEN** the server returns the categories of AI models executed during case processing along with item counts.
