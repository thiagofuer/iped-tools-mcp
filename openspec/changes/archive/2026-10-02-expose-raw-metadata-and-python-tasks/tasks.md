# Tasks

## 1. Service Layer Implementation (IpedCoreService)

- [x] 1.1 Expand `WHITELIST_PREFIXES` in `IpedCoreService` to include `ai:`, `nsfw`, `hardware-wallet-`, `faceage:` and route them to `forensic` and `extra` semantic blocks in `sanitizeProperties`, verified by unit tests.
- [x] 1.2 Implement wildcard key filtering logic and dual-mode extraction (`raw: boolean`, `keys: List<String>`) in `IpedCoreService.getDocumentMetadata` and `getDocumentMetadataBatch`, verified by unit tests.
- [x] 1.3 Add the `crypto` domain and expand the `ai` domain in `FORENSIC_DOMAINS` within `IpedCoreService`, verified by checking `getPropertyDictionary` output.
- [x] 1.4 Expand `AI_FILTERS` in `IpedCoreService` to support combined neural `csam`, `crypto_wallets`, `age_estimation`, and `nsfw` detection queries, verified by query syntax validation.
- [x] 1.5 Implement comprehensive unit tests in `IpedCoreServiceTest` covering raw metadata extraction, wildcard filtering, and IPED Python task field resolution.

## 2. MCP Tools Exposure & Annotations

- [x] 2.1 Update `DocumentMetadataTool.getDocumentMetadata` to expose optional `@ToolArg` parameters `raw` and `keys` with guidance annotations for LLMs, verified by compilation.
- [x] 2.2 Update `PropertyDictionaryTool` descriptions and domain parameter definitions to document the new `crypto` domain and neural AI properties, verified by schema inspection.
- [x] 2.3 Update `AiDetectionTool.queryAiDetections` and `listAiFilters` annotations describing `csam`, `crypto_wallets`, `age_estimation`, and `nsfw` filter types, verified by schema inspection.
- [x] 2.4 Update and expand `McpToolsTest` to validate MCP tool invocations for raw metadata, key filtering, and expanded AI detections.

## 3. Packaging & Forensic Validation

- [x] 3.1 Run full Maven test suite (`mvn test`) verifying all tests pass with zero regressions.
- [x] 3.2 Execute `scripts/package_app.ps1` to recompile and package the native Windows binary in `dist/IPED-Tools-MCP/`.
- [x] 3.3 Perform forensic verification against sample case ensuring untruncated raw metadata and Python task queries operate correctly.
