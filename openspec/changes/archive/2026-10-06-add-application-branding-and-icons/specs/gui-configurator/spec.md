# Spec Delta

## ADDED Requirements

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
