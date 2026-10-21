# Spec Delta

## Purpose

Defines how communication events (messages, calls, emails) are extracted and normalized from the properties indexed by IPED, so that every communication analysis tool shares one verifiable definition of who communicated with whom, through which channel, in which direction and when, without asserting device ownership.

## ADDED Requirements

### Requirement: Event Extraction from Indexed Properties
The server SHALL build communication events from items of the active case that carry `Communication:From` or `Communication:To`, reading only the indexed properties of those items and never modifying the case.

#### Scenario: Items with communication properties become events
- **WHEN** a communication analysis tool runs on a case with items carrying `Communication:From` and `Communication:To`
- **THEN** each such item yields one event holding its item id, timestamp, channel, direction, sender, recipients and group indicator

#### Scenario: Items without sender and recipients are ignored
- **WHEN** an item has neither `Communication:From` nor `Communication:To`
- **THEN** it does not produce an event and does not affect any count

### Requirement: Channel Resolution from Application Properties
The server SHALL resolve the channel of each event from, in order, `ufed:SourceApplication`, `accountType` and the `contentType` of the item, and SHALL NOT infer the channel from the free text of `category` alone.

#### Scenario: UFED instant message resolves to its application
- **WHEN** an item has `contentType` `application/x-ufed-instantmessage` and `ufed:SourceApplication` equal to `Viber`
- **THEN** the event channel is `Viber`

#### Scenario: Native IPED parser message resolves through account type
- **WHEN** an item has `contentType` `message/x-whatsapp-message`, no `ufed:SourceApplication` and `accountType` equal to `WhatsApp`
- **THEN** the event channel is `WhatsApp`

#### Scenario: Call and email resolve through content type and category
- **WHEN** an item has `contentType` `application/x-ufed-call`, or `category` `Emails`
- **THEN** the event kind is `call` or `email` respectively and the channel identifies the call or email source

#### Scenario: Channel cannot be determined
- **WHEN** none of the channel properties is present
- **THEN** the event channel is `unknown` and the event is still counted

### Requirement: Explicit Direction Only
The server SHALL set the direction of an event to `INCOMING` or `OUTGOING` only when `Communication:Direction` or `ufed:Direction` holds exactly that value (case-insensitive), and SHALL set it to `UNKNOWN` otherwise, never defaulting to incoming.

#### Scenario: Outgoing value is not mistaken for incoming
- **WHEN** an item has `Communication:Direction` equal to `Outgoing`
- **THEN** the event direction is `OUTGOING` and it is not counted as incoming

#### Scenario: Call direction comes from the UFED property
- **WHEN** an item has `ufed:Direction` equal to `Incoming` and no `Communication:Direction`
- **THEN** the event direction is `INCOMING` and the direction source is reported as `ufed:Direction`

#### Scenario: Direction is absent
- **WHEN** an item has neither direction property
- **THEN** the event direction is `UNKNOWN` and no direction is inferred from the identity of the sender

### Requirement: No Device Owner Assumption
The server SHALL NOT use the device owner information, `ufed:PhoneOwner` or `Conversation:Account` to decide the direction of an event or to exclude any identity from results, and SHALL NOT label any identity as the device owner.

#### Scenario: Ranking does not depend on owner information
- **WHEN** a communication analysis tool runs on a case where owner information is missing or contradictory
- **THEN** results are identical to those produced when owner information is available

### Requirement: Party Identity Resolution
The server SHALL identify each party using the structured properties `Communication:From:id|name|phoneNumber` and `Communication:To:id|name|phoneNumber` when present, falling back to parsing the raw `Communication:From`/`Communication:To` values, and SHALL merge identifiers that normalize to the same phone number or email address into one identity with its aliases.

#### Scenario: Structured identifier is preferred
- **WHEN** an item has `Communication:From:id` equal to `+15550101181` and `Communication:From:name` equal to `Carlos Teste`
- **THEN** the party identity is `+15550101181` with display name `Carlos Teste`

#### Scenario: Same phone appears in different formats
- **WHEN** two events refer to the same phone number as `+1 555-010-1181` and `15550101181`
- **THEN** both events are attributed to the same identity and both representations are listed as aliases

#### Scenario: System and anonymous senders
- **WHEN** a party value is `System Message`, `status@broadcast`, empty or another known non-person placeholder
- **THEN** the party is discarded and does not become a contact node

### Requirement: Group Event Handling
The server SHALL treat an event as a group event when `isGroupMessage` is true or the recipient identifies a group, attributing the event to the group and to its sender instead of to a one-to-one relationship.

#### Scenario: Group message does not create person-to-person edge
- **WHEN** an item has `isGroupMessage` equal to `true`, `Communication:From` equal to a person and `Communication:To` equal to a group name
- **THEN** the event links the person to the group identity and is excluded from direct one-to-one metrics

### Requirement: Event Time Resolution
The server SHALL timestamp each event using `Communication:Date`, falling back to `timeStamp` and then to `created`, normalized to ISO-8601, and SHALL report events without any usable timestamp as having no date instead of using the current time.

#### Scenario: Communication date is present
- **WHEN** an item has `Communication:Date`
- **THEN** the event timestamp is that value normalized to ISO-8601 and is used for first/last interaction and monthly aggregation

#### Scenario: No usable date
- **WHEN** an item has none of the date properties
- **THEN** the event is counted in totals but excluded from first/last interaction and monthly histograms

### Requirement: Coverage Diagnostics
The server SHALL report, with every aggregated result, how many events were analyzed and how many had explicit direction, a resolved channel and a timestamp, so that consumers can judge the confidence of the result.

#### Scenario: Diagnostics accompany results
- **WHEN** any communication analysis tool returns a result
- **THEN** the response includes the analyzed event count and the counts and percentage of events with explicit direction, resolved channel and timestamp
