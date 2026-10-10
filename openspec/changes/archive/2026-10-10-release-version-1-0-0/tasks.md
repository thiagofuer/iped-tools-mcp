# Tasks

## 1. Version Promotion & Build Verification

- [x] 1.1 Update `<version>1.0.0-SNAPSHOT</version>` to `<version>1.0.0</version>` in `pom.xml` and verify `mvn help:evaluate -Dexpression=project.version -q -DforceStdout` prints `1.0.0`
- [x] 1.2 Run `mvn clean test` and verify that all 52 unit tests execute and pass without regressions
- [x] 1.3 Compile production runner JAR via `mvn package -DskipTests` and verify `target/iped-tools-mcp-1.0.0-runner.jar` responds to `--version` with `IPED Tools MCP v1.0.0`

## 2. Native Packaging & Cryptographic Manifest

- [x] 2.1 Execute `scripts/package_app.ps1` and verify `dist/IPED-Tools-MCP-1.0.0-windows-x64-portable.zip` is generated
- [x] 2.2 Execute `scripts/package_msi.ps1` and verify `dist/IPED-Tools-MCP-1.0.0.msi` is generated from the application image with `localization/` embedded
- [x] 2.3 Execute `scripts/test_native_exe.ps1` against test IPED case and verify all 10 native executable tests pass
- [x] 2.4 Verify and inspect `dist/SHA256SUMS.txt` ensuring SHA-256 checksums are registered for both distribution packages

## 3. GitFlow Governance, Branching & Release Tagging

- [x] 3.1 Commit version update on branch `develop` with commit message `chore(release): preparar versao de lancamento 1.0.0`
- [x] 3.2 Create canonical production branch `main` from `develop` (`git checkout -b main`)
- [x] 3.3 Create annotated Git tag `v1.0.0` on the release commit (`git tag -a v1.0.0 -m "Release v1.0.0 - Assistente Forense IPED Tools MCP"`)
- [x] 3.4 Push `develop`, `main`, and tag `v1.0.0` to remote repository `origin` (`git push origin develop`, `git push -u origin main`, `git push origin v1.0.0`)
