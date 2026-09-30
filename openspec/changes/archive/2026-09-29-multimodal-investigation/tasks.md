# Tasks: Multimodal Investigation and Visual AI Capabilities

## 1. Service Layer Implementation

- [x] 1.1 Implement `getThumbnailBase64(int itemId, int maxDim)` in `IpedCoreService.java` utilizing `ImageThumbTask` and AWT scaling with Base64 encoding.
- [x] 1.2 Implement similarity search methods (`searchSimilarImages`, `searchSimilarFaces`, `searchSimilarDocuments`) in `IpedCoreService.java` integrating with IPED core similarity searchers.
- [x] 1.3 Implement AI detection discovery (`listAiFilters`) and queries (`queryAiDetections`) in `IpedCoreService.java` mapping filter types to Lucene index fields.

## 2. MCP Tools Implementation

- [x] 2.1 Create `MultimodalTool.java` exposing `@Tool get_item_thumbnail` returning MCP image content blocks paired with metadata descriptions.
- [x] 2.2 Create `SimilaritySearchTool.java` exposing `@Tool search_similar_images`, `search_similar_faces`, and `search_similar_documents`.
- [x] 2.3 Create `AiDetectionTool.java` exposing `@Tool list_ai_filters` and `query_ai_detections`.

## 3. Integration Testing & Native Verification

- [x] 3.1 Verify compilation with `mvn clean test-compile` and test Base64 image payload generation against test images.
- [x] 3.2 Execute automated test script verifying similarity queries and AI detection filters against real IPED case data.
- [x] 3.3 Rebuild native Windows application via `scripts/package_app.ps1` and verify with `scripts/test_native_exe.ps1`.
