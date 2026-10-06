# Proposal

## Why

IPED Tools MCP currently lacks custom visual branding on Windows, displaying the default generic executable icon in Windows Explorer, taskbar, desktop shortcuts, and window headers. Providing professional forensic branding with dedicated icon assets (`app.ico` and high-resolution `icon.jpg`) enhances application identification for forensic examiners, reinforces software integrity, and completes the native desktop user experience.

## What Changes

- Integrate `src/main/resources/images/app.ico` into `jpackage` execution parameters in both `scripts/package_app.ps1` and `scripts/package_msi.ps1` so that the compiled `IPED-Tools-MCP.exe` and `.msi` installers embed the icon directly into the Windows binary header and desktop/start menu shortcuts.
- Update `MainWindow.java` to load `/images/icon.jpg` from the classpath and apply multi-resolution window icons (`16x16`, `24x24`, `32x32`, `48x48`, `64x64`, `128x128`, `256x256`) via `JFrame.setIconImages()` for high-DPI crispness.
- Enrich the graphical user interface by displaying a scaled visual logo in `createHeaderPanel()` next to the main title and inside `showAboutDialog()`.
- Implement safe fallback handling so that missing image resources never disrupt application startup or GUI initialization.

## Capabilities

### New Capabilities

*(None)*

### Modified Capabilities

- `native-packaging`: Requires `jpackage` to embed `app.ico` as the native Windows application icon in both portable and MSI builds.
- `gui-configurator`: Requires `MainWindow` to display application branding and multi-resolution window icons derived from high-resolution icon assets.

## Impact

- **Build / Packaging**: Updates `scripts/package_app.ps1` and `scripts/package_msi.ps1` with `--icon`.
- **GUI**: Updates `MainWindow.java` with image loading, header logo, about modal logo, and window icons.
- **Resources**: Uses existing assets `src/main/resources/images/app.ico` and `src/main/resources/images/icon.jpg`.
- **Runtime / API**: No breaking changes or impacts on the headless MCP STDIO engine.
