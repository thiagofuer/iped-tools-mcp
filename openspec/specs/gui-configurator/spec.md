# GUI Configurator Specification

## Purpose
Provides a native Windows Swing graphical user interface designed for forensic examiners without programming knowledge. Enables intuitive case folder selection, visual index validation, live diagnostic monitoring, and 1-click configuration generation for client LLMs (LM Studio, Claude Desktop).

## Requirements

### Requirement: Native Case Selection and Validation
The GUI SHALL provide a directory picker (`JFileChooser`) to select an IPED case folder and validate that it contains the expected index and configuration subdirectories.

#### Scenario: User selects an IPED case folder
- **WHEN** the user selects a folder and clicks Open
- **THEN** the system verifies the existence of `iped/index` or `iped/conf`, updates the status badge to loaded, and begins background statistical calculation.

#### Scenario: User selects an invalid folder
- **WHEN** the user picks a folder that lacks IPED case markers
- **THEN** the system displays an error alert and keeps the previous valid case loaded.

### Requirement: Summary Statistics Display
The GUI SHALL display an executive summary of the selected case, showing total indexed items, active categories count, existing bookmarks, and index health.

#### Scenario: Case analysis finishes loading
- **WHEN** the asynchronous case loader completes
- **THEN** the labels for total items and categories update with formatted numbers, and an informative log line is appended to the diagnostic console.

### Requirement: 1-Time LM Studio Configuration Assistance
The GUI SHALL provide dedicated copy buttons and instructions to configure local LLM clients (LM Studio, Claude Desktop) with a single-time `--stdio` setup.

#### Scenario: User copies configuration parameters
- **WHEN** the user clicks "Copiar Caminho" or "Copiar Argumentos"
- **THEN** the exact executable path or `--stdio` arguments are copied to the system clipboard, accompanied by a visual confirmation notification.

#### Scenario: User toggles fixed case pinning
- **WHEN** the user selects the "Fixar caso no comando (--case)" checkbox
- **THEN** the arguments field dynamically updates to append `--case "<path>"` for examiners requiring fixed configurations.

### Requirement: Diagnostic Console Logging
The GUI SHALL maintain a scrolling text console capturing operational events, case loading milestones, and synchronization events in real time.

#### Scenario: Operational events occur
- **WHEN** cases are opened, synchronized, or errors are encountered
- **THEN** timestamped log entries are appended to the console area.

### Requirement: Application Visual Branding and Window Icons
The graphical user interface (`MainWindow`) SHALL display visual branding loaded from the application resources (`/images/icon.jpg`), including multi-resolution window icons for taskbar and title bar display, a logo icon in the main header panel, and visual branding in the About modal dialog.

#### Scenario: Examiner launches the graphical interface
- **WHEN** `MainWindow` initializes
- **THEN** the window displays high-DPI multi-resolution icons (`16x16`, `24x24`, `32x32`, `48x48`, `64x64`, `128x128`, `256x256`) on the title bar and taskbar, and renders the logo in the header panel alongside the application title.

#### Scenario: Examiner opens the About dialog
- **WHEN** the examiner clicks the "Sobre" button
- **THEN** the modal dialog displays the application logo alongside version and project information.

#### Scenario: Image resource is unavailable
- **WHEN** the application image resource `/images/icon.jpg` cannot be found or fails to load
- **THEN** the GUI initializes gracefully without throwing unhandled exceptions, falling back to default window chrome.

