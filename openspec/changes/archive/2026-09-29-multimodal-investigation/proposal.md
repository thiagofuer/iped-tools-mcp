# Proposal: Multimodal Investigation and Visual AI Capabilities

## Why
Forensic examiners analyze thousands of visual media items (photographs, screenshots, scanned documents, seized weapons, and facial imagery) during an investigation. Modern local and cloud LLMs (such as Claude 3.5 Sonnet, Qwen2-VL, and Llama 3.2 Vision) support multimodal input, yet IPED Tools MCP currently only serves textual representations. Furthermore, IPED contains powerful internal computer vision algorithms—including perceptual image similarity, FaceNet facial recognition, and deep learning detectors for weapons, drugs, and CSAM—which are currently inaccessible via MCP.

Enabling visual inspection and AI similarity tools allows the LLM to visually analyze evidence, identify persons of interest across cases, and flag high-risk media without requiring human manual review of every frame.

## What Changes
- **Thumbnail and Visual Extraction (`get_item_thumbnail`)**: Return Base64-encoded JPEG image payloads conforming to MCP's `image` content block specification, enabling multimodal LLMs to read handwriting, inspect vehicle license plates, and examine damaged documents.
- **Visual Similarity Search (`search_similar_images`)**: Leverage IPED's perceptual image hashing and feature vectors to find crops, edits, or recompressed copies of a reference picture.
- **Facial Recognition Search (`search_similar_faces`)**: Search across all case media for photos containing faces matching a designated target person.
- **Similar Document Search (`search_similar_documents`)**: Locate text documents with statistically similar phrasing or minhash signatures.
- **AI Detections Catalog & Query (`list_ai_filters`, `query_ai_detections`)**: Expose pre-computed machine learning detections from IPED (firearms, illicit drugs, CSAM, adult content, and automatic audio transcripts).

## Capabilities

### New Capabilities
- `multimodal-investigation`: Provides image thumbnail delivery, visual similarity matching, facial recognition clustering, and access to automated AI detections.

### Modified Capabilities
None.

## Impact
- **Service Layer**: Connects `IpedCoreService` to IPED's `ImageThumbTask`, `SimilarImagesSearch`, `SimilarFacesSearch`, and AI detection Lucene fields.
- **MCP Tools**: Introduces `MultimodalTool.java` and `SimilaritySearchTool.java`.
- **Performance**: Thumbnail generation leverages IPED's pre-computed thumbnail cache, ensuring fast execution without blocking the event loop.
