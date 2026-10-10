# Spec Delta

## Purpose

Defines the structure, language, content coverage, and sanitization rules of the end-user wiki maintained as versioned Markdown in `docs/wiki/`, so examiners can install, configure, and use IPED Tools MCP correctly.

## ADDED Requirements

### Requirement: Single Versioned Source for Wiki Content
All wiki pages SHALL be authored as Markdown files under `docs/wiki/` in the main repository, which is the single source of truth. Content edited directly in the GitHub Wiki UI is not authoritative and MAY be overwritten by publication.

#### Scenario: Contributor updates a wiki page
- **WHEN** a contributor needs to change wiki content
- **THEN** the change is made to a file under `docs/wiki/` through the regular GitFlow pull request process

#### Scenario: Wiki navigation files are present
- **WHEN** a reviewer inspects `docs/wiki/`
- **THEN** it contains `Home.md` as the landing page and `_Sidebar.md` providing navigation to every top-level section

### Requirement: Brazilian Portuguese Language
All wiki pages SHALL be written in Brazilian Portuguese (pt-BR), using a flat page layout without language prefixes.

#### Scenario: Examiner reads any wiki page
- **WHEN** an examiner opens any page published from `docs/wiki/`
- **THEN** the page content, headings, and navigation labels are in pt-BR

### Requirement: Getting Started Coverage
The wiki SHALL document system requirements, the official download location (`www.mcp.ipedtools.com.br`), SHA-256 integrity verification against `SHA256SUMS.txt`, and installation of both the MSI installer and the portable ZIP package.

#### Scenario: Examiner installs the product for the first time
- **WHEN** an examiner follows the Getting Started pages from the Home page
- **THEN** the examiner can download the package, verify its SHA-256 hash, and install it via MSI or portable ZIP without consulting the README

### Requirement: Supported AI Client Configuration Guides
The wiki SHALL provide one configuration guide per supported AI client: Claude Desktop, Cursor, Antigravity, LM Studio, and Claude Code. Each guide SHALL show the `--stdio` invocation and explain that `--case` is optional because the active case is shared.

#### Scenario: Examiner configures a GUI-supported client
- **WHEN** an examiner opens the guide for Claude Desktop, Cursor, Antigravity, or LM Studio
- **THEN** the guide explains how to generate the configuration with the graphical configurator and where to paste it in that client

#### Scenario: Examiner configures Claude Code
- **WHEN** an examiner opens the Claude Code guide
- **THEN** the guide provides the command-line registration of the server with the `--stdio` argument

#### Scenario: Examiner works in an air-gapped laboratory
- **WHEN** an examiner looks for a fully offline setup
- **THEN** the wiki identifies LM Studio with a local model as the recommended air-gapped path

#### Scenario: Examiner looks for an undocumented client
- **WHEN** an examiner searches the wiki for a client outside the five supported ones
- **THEN** no page claims configuration support for that client

### Requirement: Usage and Tool Reference Coverage
The wiki SHALL document the graphical configurator and active case synchronization, the `start_case` prompt and investigation workflow, the full catalog of MCP tools exposed by the server, Lucene query syntax, and the property dictionary domains.

#### Scenario: Examiner learns which tool answers a question
- **WHEN** an examiner opens the tool catalog page
- **THEN** every MCP tool exposed by the current release is listed with its purpose and at least one example question in pt-BR

#### Scenario: Examiner switches cases
- **WHEN** an examiner reads the configurator page
- **THEN** it explains that the active case is shared between the GUI and the chat via the user profile state and can be changed with `open_case` without reconfiguring the client

### Requirement: Forensic Practices and Troubleshooting Coverage
The wiki SHALL document read-only evidence access, offline operation, the only permitted writes (bookmarks and checked status reviewable in IPED Desktop), operational non-goals, and a troubleshooting/FAQ page for common setup failures.

#### Scenario: Examiner verifies chain-of-custody guarantees
- **WHEN** an examiner reads the forensic practices page
- **THEN** it states that Lucene indexes and SQLite databases are never modified and that only `bookmarks.iped` and checked status may change

#### Scenario: Client cannot connect to the server
- **WHEN** an examiner's AI client reports that the server failed to start or exposes no tools
- **THEN** the troubleshooting page lists diagnostic steps including `--version` verification and executable path checks

### Requirement: Developer Documentation Coverage
The wiki SHALL contain a development section covering architecture diagrams, dual-mode execution flow, CLI options, build, automated tests, and native packaging, and SHALL link to `CONTRIBUTING.md` and `CHANGELOG.md` in the repository instead of duplicating them.

#### Scenario: Developer prepares a build
- **WHEN** a developer opens the development section
- **THEN** it documents prerequisites, Maven build, test execution, and ZIP/MSI packaging commands

### Requirement: Sanitized Examples in Public Documentation
Every path, case name, and identifier in wiki content SHALL be fictitious (e.g. `C:\casos_forenses\caso_operacao_01`) and SHALL NOT expose real case folders, personal drive layouts, or investigator identities.

#### Scenario: Reviewer audits wiki examples
- **WHEN** a reviewer inspects configuration snippets and screenshots in `docs/wiki/`
- **THEN** all case paths and names are fictitious and no real evidence identifiers are visible
