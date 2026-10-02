# Spec Delta

## MODIFIED Requirements

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
