# Design: Multimodal Investigation and Visual AI Capabilities

## Context
See `proposal.md` for motivation. IPED already computes visual perceptual hashes, facial vectors (via dlib/FaceNet), and deep learning classification tags during case indexing. These are saved in index fields and thumbnail stores (`iped/index/` or case data folders).

## Goals / Non-Goals

**Goals:**
- Provide fast visual thumbnail delivery using MCP's standard `image` content format.
- Connect MCP tools directly to IPED's in-process `SimilarImagesSearch`, `SimilarFacesSearch`, and `SimilarDocumentSearch`.
- Provide query wrappers over indexed deep learning detections (`weapons`, `drugs`, `nudity`, `faces`, `audio_transcripts`).

**Non-Goals:**
- Running live GPU-heavy neural network re-training or processing inside the MCP process (queries rely on precomputed index tags).
- Streaming video files over STDIO (only static thumbnails/frames are provided).

## Decisions

### Decision 1: Pre-computed Thumbnail Store First, On-Demand Fallback
- **Choice:** Attempt to retrieve pre-generated thumbnails from `ImageThumbTask.getThumbnail(item)`. If absent (e.g. for certain document types), decode the first page on-demand and scale down using AWT/ImageIO.
- **Rationale:** Minimizes latency (< 50ms) for media files while still supporting documents.

### Decision 2: 512px Dimension Default for Token Efficiency
- **Choice:** Standardize default thumbnail dimensions to 512px on the longest edge while preserving aspect ratio.
- **Rationale:** 512px provides optimal legibility for handwritten notes, license plates, and receipts while consuming minimal context tokens in multimodal models.

### Decision 3: Structured AI Detection Queries
- **Choice:** Map `filter_type` strings to tested, internal Lucene queries:
  - `weapons` -> `hasWeapon:true OR category:"weapons"`
  - `drugs` -> `hasDrug:true OR category:"drugs"`
  - `nudity` -> `isNudity:true OR category:"nudity"`
  - `faces` -> `hasFace:true`
  - `audio_transcripts` -> `hasAudioTranscript:true OR audioTranscript:*`

## Risks / Trade-offs

- **[Risk] High memory usage when handling large image streams**  
  → *Mitigation:* Use try-with-resources with buffered image streams and immediate garbage collection hints for byte arrays.
- **[Risk] Clients lacking multimodal capabilities**  
  → *Mitigation:* Always pair the image content block with a companion textual description block indicating the item ID, filename, and resolution.
