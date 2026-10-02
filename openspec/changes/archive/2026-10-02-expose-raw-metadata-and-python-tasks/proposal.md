# Proposal: Expose Raw Metadata and Python Task Artifacts

## Why
Digital forensic examinations frequently require exhaustive, untruncated metadata inspection for specific evidence items (e.g. detailed Windows Registry records, mobile configuration XMLs, and EXIF profiles), while the current `get_document_metadata` tool enforces aggressive 500-character and 10-item truncations. Furthermore, IPED includes powerful Python and JavaScript tasks (`SearchHardwareWallets.py`, `CSAMDetectorTask.py`, `NSFWNudityDetectTask.py`, `AgeEstimationTask.py`, `FaceRecognitionTask.py`) whose resulting properties (`Hardware-Wallet-*`, `ai:csamDetector:*`, `nsfw_nudity_score`, `faceAge:*`) are currently discarded by MCP sanitization whitelists and missing from tool catalogs. Exposing full-fidelity raw metadata retrieval with surgical key filtering alongside first-class support for IPED Python tasks empowers LLMs with comprehensive investigative depth while preserving token efficiency.

## What Changes
- **Raw and Filtered Metadata Retrieval**:
  - Add optional `raw` boolean parameter to `get_document_metadata` (defaults to `false`). When `true`, returns un-truncated metadata without arbitrary semantic grouping or character/item limits (preserving only low-level Lucene internal engine buffer exclusions and `content` text redirection).
  - Add optional `keys` list parameter to `get_document_metadata` supporting exact field names and wildcard patterns (e.g. `["Hardware-Wallet-*", "ai:*", "Communication:*"]`), returning only the requested properties.
- **Support for IPED Python/JS Task Metadata**:
  - Update `IpedCoreService` sanitization whitelists to permit prefixes: `ai:`, `nsfw_`, `hardware-wallet-`, `faceage:`.
  - Partition Python task properties into appropriate semantic blocks in standard mode (`forensic` for `ai:`/`nsfw_`, `extra` for `hardware-wallet-` and `faceage:`).
- **Enriched Forensic Property Dictionary**:
  - Add or expand domains in `get_property_dictionary`:
    - Document `ai` domain with `ai:csamDetector:csam`, `ai:csamDetector:label`, `ai:csamDetector:triggerFrame`, `faceAge:labels`, `faceAge:count:<label>`, `nsfw_nudity_score`.
    - Document `crypto` / `system` domain with `Hardware-Wallet-Found`, `Hardware-Wallet-VendorName`, `Hardware-Wallet-DeviceName`.
- **Expanded AI Detections**:
  - Update `query_ai_detections` and `list_ai_filters` to support `csam` detection queries matching both hash database hits and neural network scores (`ai:csamDetector:csam:[0.6 TO 1.0]`), plus new dedicated filter types `crypto_wallets` (`Hardware-Wallet-Found:true`), `age_estimation` (`faceAge:count:Child:[1 TO *]`), and `nsfw` (`nsfw_nudity_score:[50 TO 100]`).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `forensic-query-tools`: Update `Requirement: Metadata Extraction` to support un-truncated raw metadata (`raw: true`) and surgical key filtering (`keys`).
- `metadata-dictionary`: Update `Requirement: Forensic Property Dictionary` to catalog IPED Python task properties across AI and hardware wallet domains.
- `multimodal-investigation`: Update `Requirement: Automated AI Detections` to support expanded filter categories for neural CSAM scores, crypto hardware wallets, age estimation, and Python NSFW detections.

## Impact
- **Code Changes**: `IpedCoreService.java`, `DocumentMetadataTool.java`, `PropertyDictionaryTool.java`, `AiDetectionTool.java`.
- **API / MCP Protocols**: Non-breaking additions of optional parameters (`raw`, `keys`) to existing tools; new filter types in `query_ai_detections`.
- **Dependencies**: No external library additions; uses existing IPED Lucene index fields directly in-process.
- **Forensic Integrity**: Maintains strict read-only guarantees on index and evidence files.
