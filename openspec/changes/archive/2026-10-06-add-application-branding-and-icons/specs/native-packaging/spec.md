# Spec Delta

## ADDED Requirements

### Requirement: Native Application Icon Branding
The packaging pipeline (`package_app.ps1` and `package_msi.ps1`) SHALL embed the dedicated Windows application icon resource (`src/main/resources/images/app.ico`) into the native executable binary and installer shortcuts using the `jpackage` `--icon` configuration option.

#### Scenario: User inspects packaged executable in Windows Explorer
- **WHEN** the user builds the portable application via `package_app.ps1`
- **THEN** `IPED-Tools-MCP.exe` in `dist/IPED-Tools-MCP/` contains the embedded icon resource and displays the forensic branding in File Explorer and the Windows taskbar.

#### Scenario: User installs application via MSI installer
- **WHEN** the user installs the application using `IPED-Tools-MCP-1.0.0.msi`
- **THEN** the Desktop and Start Menu shortcuts, as well as the Windows Add/Remove Programs entry, display the embedded application icon.
