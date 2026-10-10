# Spec Delta

## MODIFIED Requirements

### Requirement: Repository Hygiene and Governance Documentation
The project repository SHALL maintain strict `.gitignore` exclusions and comprehensive documentation for GitFlow development, semantic versioning, binary distribution, and forensic user personas across criminal, judicial, and private litigation contexts. The `README.md` SHALL act as a concise entry point that directs users to the GitHub Wiki for installation, configuration, usage, tool reference, and development guides.

#### Scenario: Contributor builds project in a clean clone
- **WHEN** a developer compiles and packages the project
- **THEN** `.gitignore` ensures that `target/`, `dist/`, `tools/`, IDE metadata, and local `.iped` state files remain untracked.

#### Scenario: Contributor reviews contribution guidelines
- **WHEN** a contributor inspects `CONTRIBUTING.md`
- **THEN** the document details the GitFlow branching model (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`), Semantic Versioning standards, and pull request procedures.

#### Scenario: User checks binary distribution in README
- **WHEN** a user or examiner accesses `README.md` to download releases or packages
- **THEN** all official executable, ZIP, and MSI installer download references link to `www.mcp.ipedtools.com.br`.

#### Scenario: User inspects forensic personas and usage profiles in README
- **WHEN** an examiner, judicial expert, or legal actor accesses `README.md` to evaluate target use cases
- **THEN** the "Personas e Perfis de Uso" section explicitly documents the roles and tool capabilities for Perito Criminal Oficial, Perito Judicial, Assistente Técnico, Analista de Inteligência Policial, and Autoridade Policial/Delegado/Promotor.

#### Scenario: User looks for setup and usage guidance in README
- **WHEN** a user accesses `README.md` to learn how to install, configure, or use the product
- **THEN** the README presents a short quickstart and links to the GitHub Wiki, and does not duplicate the tool catalog, per-client configuration guides, architecture, or build instructions.

#### Scenario: README lists supported AI clients
- **WHEN** the README mentions compatible AI clients
- **THEN** it names only clients that have a configuration guide in the GitHub Wiki
