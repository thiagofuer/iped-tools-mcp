# Tasks

## 1. Native Windows Packaging Scripts

- [x] 1.1 Update `scripts/package_app.ps1` to conditionally append `--icon` with `src/main/resources/images/app.ico` to `$jpackageArgs`, and verify parameter syntax.
- [x] 1.2 Update `scripts/package_msi.ps1` to conditionally append `--icon` with `src/main/resources/images/app.ico` to `$msiArgs`, and verify parameter syntax.

## 2. Graphical Interface Branding & Window Icons

- [x] 2.1 Update `MainWindow.java` with a safe loader for `/images/icon.jpg` and set multi-resolution icons (`16x16` through `256x256`) via `JFrame.setIconImages()`, verifying fallback behavior.
- [x] 2.2 Update `createHeaderPanel()` in `MainWindow.java` to render the 48x48 visual logo in the header bar next to the application title.
- [x] 2.3 Update `showAboutDialog()` in `MainWindow.java` to render the 64x64 visual logo in the About modal dialog.

## 3. Verification & Compilation

- [x] 3.1 Run `mvn test-compile` to verify compilation and resource resolution.
- [x] 3.2 Run `openspec validate add-application-branding-and-icons` to ensure complete specification compliance.
