# Proposal: Communications Network Graph and Contact Analytics

## Why
Investigations involving organized crime, conspiracy, fraud networks, and trafficking require analyzing the structure of relationships between individuals. IPED Desktop provides a dedicated Graph Analytics module (`AppGraphAnalytics`) to visualize who communicates with whom, identify central network hubs, and discover intermediaries between suspects.

Currently, the LLM must query chat messages and phone calls individually, making it difficult or impossible to perceive high-level communication patterns, total interaction volumes, or shared contacts across multiple seized devices.

## What Changes
- **Contact Ranking by Interaction Volume (`get_top_contacts`)**: Aggregate and rank the most frequent interlocutors across all communication channels (WhatsApp, Telegram, SMS, phone calls).
- **Communication Network Graph Extraction (`get_communications_graph`)**: Return a topological graph structure consisting of nodes (individuals, phone numbers, accounts) and weighted edges (interaction counts, channels, first and last contact timestamps).

## Capabilities

### New Capabilities
- `communications-graph`: Provides network relationship extraction and contact interaction ranking across messaging and calling artifacts.

### Modified Capabilities
None.

## Impact
- **Service Layer**: Implements contact aggregation and edge weighting across communication categories in `IpedCoreService.java`.
- **MCP Tools**: Introduces `CommunicationsGraphTool.java`.
- **Performance**: Aggregates indexed communication fields (`Communication:From`, `Communication:To`, `phone`) using Lucene facets or term collectors.
