# Spec Delta

## Purpose

Publishes the versioned `docs/wiki/` content to the GitHub Wiki automatically and in one direction, so the Wiki tab always reflects the released version of IPED Tools MCP.

## ADDED Requirements

### Requirement: Automatic Publication on Release Branch
The repository SHALL publish `docs/wiki/` to the GitHub Wiki automatically whenever a push to the `main` branch changes any file under `docs/wiki/`. Pushes to other branches SHALL NOT publish.

#### Scenario: Release merged into main with wiki changes
- **WHEN** a push to `main` modifies files under `docs/wiki/`
- **THEN** the publication workflow runs and the GitHub Wiki reflects the content of `docs/wiki/` at that commit

#### Scenario: Push to develop with wiki changes
- **WHEN** a push to `develop` or a `feature/*` branch modifies files under `docs/wiki/`
- **THEN** the GitHub Wiki is not changed

#### Scenario: Push to main without wiki changes
- **WHEN** a push to `main` does not modify any file under `docs/wiki/`
- **THEN** the publication workflow does not run automatically

### Requirement: Manual Republication
The publication workflow SHALL support manual triggering by maintainers to republish the current `main` content.

#### Scenario: Maintainer forces republication
- **WHEN** a maintainer manually dispatches the publication workflow
- **THEN** the GitHub Wiki is overwritten with the current content of `docs/wiki/` on `main`

### Requirement: One-Way Mirror Semantics
Publication SHALL make the GitHub Wiki an exact mirror of `docs/wiki/`: pages added, changed, or removed in `docs/wiki/` are added, changed, or removed in the Wiki, and edits made directly in the Wiki are overwritten.

#### Scenario: Page removed from docs/wiki
- **WHEN** a file is deleted from `docs/wiki/` and the change is published
- **THEN** the corresponding page no longer exists in the GitHub Wiki

#### Scenario: Nothing changed since last publication
- **WHEN** the workflow runs and the Wiki already matches `docs/wiki/`
- **THEN** the workflow finishes successfully without creating an empty Wiki commit

### Requirement: Least-Privilege Credentials
The publication workflow SHALL authenticate using the repository-scoped workflow token with only content write permission and SHALL NOT require personal access tokens or store secrets in the repository.

#### Scenario: Reviewer audits workflow permissions
- **WHEN** a reviewer inspects the publication workflow definition
- **THEN** it declares only `contents: write` permission and references no personal access token

### Requirement: Clear Failure When Wiki Is Not Initialized
The publication workflow SHALL fail with an explicit message instructing maintainers to create the first Wiki page via the GitHub web UI when the Wiki repository does not yet exist.

#### Scenario: First run before Wiki initialization
- **WHEN** the workflow runs and the `.wiki.git` repository cannot be cloned
- **THEN** the run fails and its log explains that the first Wiki page must be created manually in the GitHub web UI
