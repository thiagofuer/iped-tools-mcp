param(
    [string]$JdkPath = "C:\Program Files\Java\jdk-21.0.12",
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

# Resolver versao dinamicamente do pom.xml se nao informada
if (-not $Version) {
    try {
        $pomXml = [xml](Get-Content (Join-Path $projectRoot "pom.xml"))
        $Version = $pomXml.project.version
    } catch {
        $Version = "1.0.0"
    }
}

# jpackage exige formato de versao estritamente numerico (ex: 1.0.0), sem sufixos como -SNAPSHOT
$numericVersion = ($Version -replace '-.*$', '').Trim()
if (-not ($numericVersion -match '^\d+(\.\d+)*$')) {
    $numericVersion = "1.0.0"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " IPED Tools MCP - Pipeline de Empacotamento Nativo Windows" -ForegroundColor Cyan
Write-Host " Versao: $Version" -ForegroundColor Cyan
Write-Host " JDK:    $JdkPath" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$jlinkExe = Join-Path $JdkPath "bin\jlink.exe"
$jpackageExe = Join-Path $JdkPath "bin\jpackage.exe"

# 1. Gerar JRE enxuto via jlink se nao existir
$runtimeDir = Join-Path $projectRoot "target\runtime"
if (-not (Test-Path $runtimeDir)) {
    Write-Host "[1/4] Gerando runtime Java 21 enxuto via jlink..." -ForegroundColor Yellow
    $modules = @(
        "java.se",
        "jdk.dynalink",
        "jdk.unsupported",
        "jdk.unsupported.desktop",
        "jdk.charsets",
        "jdk.localedata",
        "jdk.crypto.ec",
        "jdk.crypto.mscapi",
        "jdk.management",
        "jdk.zipfs",
        "jdk.naming.dns",
        "jdk.xml.dom",
        "jdk.jsobject",
        "jdk.net",
        "jdk.nio.mapmode",
        "jdk.random",
        "jdk.security.auth",
        "jdk.security.jgss"
    ) -join ","

    & $jlinkExe --add-modules $modules `
        --strip-debug `
        --no-man-pages `
        --no-header-files `
        --compress zip-6 `
        --output $runtimeDir
    Write-Host "  -> Runtime gerado em $runtimeDir" -ForegroundColor Green
} else {
    Write-Host "[1/4] Runtime Java 21 enxuto já existe em $runtimeDir" -ForegroundColor Green
}

# 2. Preparar pasta de input para jpackage
$inputDir = Join-Path $projectRoot "target\package-input"
if (Test-Path $inputDir) { Remove-Item -Recurse -Force $inputDir }
New-Item -ItemType Directory -Path $inputDir | Out-Null

$expectedJar = Join-Path $projectRoot "target\iped-tools-mcp-$Version-runner.jar"
if (Test-Path $expectedJar) {
    $runnerJar = $expectedJar
    $runnerJarName = "iped-tools-mcp-$Version-runner.jar"
} else {
    $candidates = Get-ChildItem -Path (Join-Path $projectRoot "target") -Filter "iped-tools-mcp-*-runner.jar" -File
    if ($candidates -and $candidates.Count -gt 0) {
        $runnerJar = $candidates[0].FullName
        $runnerJarName = $candidates[0].Name
    } else {
        throw "Runner JAR não encontrado em target\. Execute 'mvn package -DskipTests' primeiro."
    }
}
Write-Host "  -> Utilizando runner JAR: $runnerJarName" -ForegroundColor Cyan
Copy-Item $runnerJar -Destination (Join-Path $inputDir $runnerJarName)

# 3. Executar jpackage para criar o app-image (binário .exe nativo com runtime embutido)
Write-Host "[2/4] Executando jpackage para criar o pacote nativo Windows..." -ForegroundColor Yellow
$distDir = Join-Path $projectRoot "dist"
$targetAppDir = Join-Path $distDir "IPED-Tools-MCP"
if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir | Out-Null }
if (-not (Test-Path $targetAppDir)) { New-Item -ItemType Directory -Path $targetAppDir | Out-Null }

$buildDestDir = Join-Path $projectRoot "target\dist-build"
if (Test-Path $buildDestDir) { Remove-Item -Recurse -Force $buildDestDir }
New-Item -ItemType Directory -Path $buildDestDir | Out-Null

$jvmOptions = @(
    "--java-options", '-Duser.dir=$APPDIR',
    "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.math=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.io=ALL-UNNAMED"
)

$jpackageArgs = @(
    "--type", "app-image",
    "--name", "IPED-Tools-MCP",
    "--app-version", $numericVersion,
    "--vendor", "IPED Open Source Project",
    "--description", "IPED Tools MCP - Conector LLM e Servidor Forense",
    "--input", $inputDir,
    "--main-jar", $runnerJarName,
    "--main-class", "br.com.ipedtools.mcp.McpApplication",
    "--runtime-image", $runtimeDir,
    "--dest", $buildDestDir
) + $jvmOptions

$iconPath = Join-Path $projectRoot "src\main\resources\images\app.ico"
if (Test-Path $iconPath) {
    Write-Host "  -> Aplicando ícone da aplicação: $iconPath" -ForegroundColor Cyan
    $jpackageArgs += @("--icon", $iconPath)
}

& $jpackageExe @jpackageArgs

if ($LASTEXITCODE -ne 0) {
    throw "Falha na execução do jpackage (código: $LASTEXITCODE)"
}

$builtAppDir = Join-Path $buildDestDir "IPED-Tools-MCP"

$activeProcesses = Get-Process -Name "IPED-Tools-MCP" -ErrorAction SilentlyContinue
if ($activeProcesses) {
    Write-Host "  -> Encerrando processos IPED-Tools-MCP em execucao para permitir substituicao dos arquivos..." -ForegroundColor Yellow
    $activeProcesses | Stop-Process -Force
    Start-Sleep -Seconds 2
}

# Limpar o diretório de destino para evitar acumular JARs ou binários de versões anteriores
if (Test-Path $targetAppDir) {
    Get-ChildItem -Path $targetAppDir -Recurse -Force | ForEach-Object {
        if ($_.IsReadOnly) { $_.IsReadOnly = $false }
    }
    Remove-Item -Recurse -Force $targetAppDir
}
New-Item -ItemType Directory -Path $targetAppDir | Out-Null

Copy-Item -Path "$builtAppDir\*" -Destination $targetAppDir -Recurse -Force
Write-Host "  -> Pacote criado em: $targetAppDir" -ForegroundColor Green

# 4. Copiar bundle de localização para dentro do pacote (raiz da aplicação)
Write-Host "[3/4] Copiando bundles de localização..." -ForegroundColor Yellow
$locSource = Join-Path $projectRoot "localization"
if (Test-Path $locSource) {
    $targetLoc = Join-Path $distDir "IPED-Tools-MCP\localization"
    if (Test-Path $targetLoc) { Remove-Item -Recurse -Force $targetLoc }
    Copy-Item -Recurse -Force $locSource $targetLoc

    # Limpar subpasta app/localization caso tenha sobrado de builds legados
    $appLoc = Join-Path $distDir "IPED-Tools-MCP\app\localization"
    if (Test-Path $appLoc) { Remove-Item -Recurse -Force $appLoc }

    Write-Host "  -> Bundles de localização copiados com sucesso para a raiz da aplicação." -ForegroundColor Green
}



# 5. Criar ZIP portatil para distribuicao rapida
Write-Host "[4/4] Gerando arquivo ZIP portatil para distribuicao..." -ForegroundColor Yellow
$zipOutput = Join-Path $distDir "IPED-Tools-MCP-$Version-windows-x64-portable.zip"
if (Test-Path $zipOutput) { Remove-Item -Force $zipOutput }
Compress-Archive -Path (Join-Path $distDir "IPED-Tools-MCP") -DestinationPath $zipOutput -CompressionLevel Optimal
$zipSizeMb = [math]::Round((Get-Item $zipOutput).Length / 1MB, 2)
Write-Host "  -> ZIP portatil gerado: $zipOutput ($zipSizeMb MB)" -ForegroundColor Green

# 6. Atualizar manifesto de somas de verificacao SHA-256 (forensic manifest)
Write-Host "  -> Atualizando manifesto SHA-256 (SHA256SUMS.txt)..." -ForegroundColor Yellow
$sha256Manifest = Join-Path $distDir "SHA256SUMS.txt"
$distPackages = Get-ChildItem -Path $distDir -File | Where-Object { $_.Name -like "*.zip" -or $_.Name -like "*.msi" }
$hashLines = @()
foreach ($pkg in $distPackages) {
    $fileHash = (Get-FileHash -Path $pkg.FullName -Algorithm SHA256).Hash
    $hashLines += "$fileHash  $($pkg.Name)"
}
# 7. Higienizar atributos e remover pasta intermediária para compatibilidade com 'mvn clean'
Write-Host "  -> Higienizando atributos e limpando arquivos intermediários..." -ForegroundColor Yellow
$finalExe = Join-Path $targetAppDir "IPED-Tools-MCP.exe"
if (Test-Path $finalExe) {
    (Get-Item $finalExe).IsReadOnly = $false
}
Get-ChildItem -Path $targetAppDir -Recurse -Force | ForEach-Object {
    if ($_.IsReadOnly) { $_.IsReadOnly = $false }
}
if (Test-Path $buildDestDir) {
    Get-ChildItem -Path $buildDestDir -Recurse -Force | ForEach-Object {
        if ($_.IsReadOnly) { $_.IsReadOnly = $false }
    }
    Remove-Item -Recurse -Force $buildDestDir
    Write-Host "  -> Pasta temporária $buildDestDir removida com sucesso." -ForegroundColor Green
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " EMPACOTAMENTO CONCLUIDO COM SUCESSO!" -ForegroundColor Green
$exePath = Join-Path $distDir "IPED-Tools-MCP\IPED-Tools-MCP.exe"
Write-Host " Executavel: $exePath" -ForegroundColor Green
Write-Host " Pacote ZIP: $zipOutput" -ForegroundColor Green
Write-Host " Checksums:  $sha256Manifest" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
