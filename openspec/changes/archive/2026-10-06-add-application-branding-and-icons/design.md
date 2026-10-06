# Design

## Context

See `proposal.md` for background and motivation. The project currently has `app.ico` and `icon.jpg` located under `src/main/resources/images/`. These need to be wired into the build scripts and the Swing GUI.

## Goals / Non-Goals

**Goals:**
- Pass `--icon` pointing to `src/main/resources/images/app.ico` to `jpackage` in `scripts/package_app.ps1` and `scripts/package_msi.ps1`.
- Wire `src/main/resources/images/icon.jpg` into `MainWindow.java` for window icons (`JFrame.setIconImages()`), the header panel branding, and the About modal dialog.
- Implement robust defensive loading so that GUI startup never fails if image assets are missing or unreadable.

**Non-Goals:**
- Runtime icon extraction from `.ico` (Java `ImageIO` natively supports JPEG/PNG; `icon.jpg` is used in Java runtime, while `app.ico` is used by Windows `jpackage`).
- Altering MCP protocol handlers or command-line options.

## Decisions

### Decision: Multi-Resolution Scaling for JFrame
- **Choice**: Generate scaled instances (`16, 24, 32, 48, 64, 128, 256`) from `icon.jpg` using `Image.SCALE_SMOOTH` and pass them to `JFrame.setIconImages()`.
- **Rationale**: Windows uses 16x16 for title bar/system menu, 32x32 for taskbar, and 48x48/64x64/128x128 for Alt+Tab and task view. Supplying all resolutions guarantees sharp, non-pixelated rendering on 100%, 125%, 150%, and 200% (4K) scaling.
- **Alternatives Considered**: Supplying a single 32x32 image. Rejected because downscaling or upscaling a single fixed size leads to blurry edges in HiDPI environments.

### Decision: Conditional `--icon` Flag in Packaging Scripts
- **Choice**: Verify `Test-Path` on the icon path before appending `--icon` to `$jpackageArgs` and `$msiArgs`.
- **Rationale**: Prevents build failures if the script is run in an environment where the resource file was moved or omitted.

### Decision: Visual Branding in Header and About Dialog
- **Choice**:
  - In `createHeaderPanel()`: Place a 48x48 icon logo to the left of the title and subtitle in `BorderLayout.WEST`.
  - In `showAboutDialog()`: Place a 64x64 logo at the top or alongside the application description.

## Risks / Trade-offs

- **[Risk: Image loading latency on startup]** → Mitigation: `icon.jpg` is ~200KB; loading and scaling 7 resolutions in memory takes <15ms, which is imperceptible on desktop initialization.
- **[Risk: Missing image on classpath]** → Mitigation: Enclose resource loading in a try-catch block with null checks, logging any warning to console without interrupting UI creation.
