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
        $Version = "1.0.1"
    }
}

# jpackage exige formato de versao estritamente numerico (ex: 1.0.0), sem sufixos como -SNAPSHOT
$numericVersion = ($Version -replace '-.*$', '').Trim()
if (-not ($numericVersion -match '^\d+(\.\d+)*$')) {
    $numericVersion = "1.0.1"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " IPED Tools MCP - Gerador de Instalador Windows (.msi)" -ForegroundColor Cyan
Write-Host " Versao: $Version" -ForegroundColor Cyan
Write-Host " JDK:    $JdkPath" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$jpackageExe = Join-Path $JdkPath "bin\jpackage.exe"
$wixDir = Join-Path $projectRoot "tools\wix311"
$candleExe = Join-Path $wixDir "candle.exe"

# 1. Verificar deteccao e bootstrapping do WiX Toolset 3.11
$wixFound = $false
if (Test-Path $candleExe) {
    $wixFound = $true
} elseif (Get-Command candle.exe -ErrorAction SilentlyContinue) {
    $wixFound = $true
    $wixDir = Split-Path -Parent (Get-Command candle.exe).Source
}

if (-not $wixFound) {
    Write-Host "[WiX] WiX Toolset 3.11 nao encontrado em $wixDir. Iniciando download oficial..." -ForegroundColor Yellow
    $wixZipUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip"
    $targetDir = Join-Path $projectRoot "target"
    if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir | Out-Null }
    $tempZip = Join-Path $targetDir "wix311-binaries.zip"

    try {
        Write-Host "  -> Baixando WiX Toolset de: $wixZipUrl" -ForegroundColor Cyan
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $wixZipUrl -OutFile $tempZip -UseBasicParsing
        
        Write-Host "  -> Extraindo arquivos para $wixDir..." -ForegroundColor Cyan
        if (-not (Test-Path $wixDir)) {
            New-Item -ItemType Directory -Path $wixDir | Out-Null
        }
        Expand-Archive -Path $tempZip -DestinationPath $wixDir -Force
        Remove-Item -Force $tempZip

        if (Test-Path (Join-Path $wixDir "candle.exe")) {
            Write-Host "  -> WiX Toolset instalado com sucesso em $wixDir" -ForegroundColor Green
            $wixFound = $true
        } else {
            throw "Falha ao extrair candle.exe em $wixDir"
        }
    } catch {
        Write-Host ""
        Write-Host "ERRO: Falha ao baixar ou extrair o WiX Toolset automaticamente: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Procedimento para Ambientes Isolados (Offline / Air-Gapped):" -ForegroundColor Yellow
        Write-Host "  1. Em uma maquina com internet, baixe 'wix311-binaries.zip' de:" -ForegroundColor Yellow
        Write-Host "     $wixZipUrl" -ForegroundColor Cyan
        Write-Host "  2. Copie e extraia o conteudo diretamente na pasta:" -ForegroundColor Yellow
        Write-Host "     $wixDir" -ForegroundColor Cyan
        Write-Host "  3. Execute novamente este script de empacotamento." -ForegroundColor Yellow
        Write-Host ""
        throw "WiX Toolset 3.11 indispensavel para gerar pacotes de instalacao MSI."
    }
}

# Adicionar WiX ao PATH para que o jpackage encontre candle.exe e light.exe
$env:PATH = "$wixDir;" + $env:PATH

$distDir = Join-Path $projectRoot "dist"
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

$appImageDir = Join-Path $distDir "IPED-Tools-MCP"
$appExe = Join-Path $appImageDir "IPED-Tools-MCP.exe"

# 2. Verificar integridade da imagem da aplicacao (app-image)
if (-not (Test-Path $appExe)) {
    Write-Host "[1/2] Imagem da aplicação não encontrada em $appImageDir. Executando scripts\package_app.ps1..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "package_app.ps1") -JdkPath $JdkPath -Version $Version
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $appExe)) {
        throw "Falha ao gerar imagem da aplicação via package_app.ps1."
    }
}

# 3. Garantir que os bundles de localização estejam presentes na imagem da aplicacao
$appLocDir = Join-Path $appImageDir "localization"
if (-not (Test-Path $appLocDir)) {
    Write-Host "  -> Sincronizando pasta de localização para $appLocDir..." -ForegroundColor Yellow
    $locSource = Join-Path $projectRoot "localization"
    if (Test-Path $locSource) {
        Copy-Item -Recurse -Force $locSource $appLocDir
    }
}

Write-Host "Executando jpackage para criar o instalador MSI a partir da imagem da aplicação..." -ForegroundColor Yellow

$msiArgs = @(
    "--type", "msi",
    "--name", "IPED-Tools-MCP",
    "--app-version", $numericVersion,
    "--vendor", "IPED Open Source Project",
    "--description", "IPED Tools MCP - Conector LLM e Servidor Forense",
    "--app-image", $appImageDir,
    "--dest", $distDir,
    "--win-dir-chooser",
    "--win-menu",
    "--win-menu-group", "IPED Tools",
    "--win-shortcut",
    "--win-upgrade-uuid", "7b6b29f0-32df-4ad0-b217-ef996f424c55"
)

$iconPath = Join-Path $projectRoot "src\main\resources\images\app.ico"
if (Test-Path $iconPath) {
    Write-Host "  -> Aplicando ícone da aplicação no instalador MSI: $iconPath" -ForegroundColor Cyan
    $msiArgs += @("--icon", $iconPath)
}

& $jpackageExe @msiArgs

if ($LASTEXITCODE -ne 0) {
    throw "Falha ao gerar MSI (codigo: $LASTEXITCODE)"
}

$msiCandidate = Join-Path $distDir "IPED-Tools-MCP-$numericVersion.msi"
if (-not (Test-Path $msiCandidate)) {
    $msiCandidate = Join-Path $distDir "IPED-Tools-MCP-$Version.msi"
}
$msiFile = $msiCandidate
if (Test-Path $msiFile) {
    $msiSizeMb = [math]::Round((Get-Item $msiFile).Length / 1MB, 2)
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " INSTALADOR MSI CRIADO COM SUCESSO!" -ForegroundColor Green
    Write-Host " Arquivo: $msiFile ($msiSizeMb MB)" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "MSI gerado com sucesso em $distDir" -ForegroundColor Green
}

# Atualizar manifesto de somas de verificacao SHA-256 (forensic manifest)
Write-Host "  -> Atualizando manifesto SHA-256 (SHA256SUMS.txt)..." -ForegroundColor Yellow
$sha256Manifest = Join-Path $distDir "SHA256SUMS.txt"
$distPackages = Get-ChildItem -Path $distDir -File | Where-Object { $_.Name -like "*.zip" -or $_.Name -like "*.msi" }
$hashLines = @()
foreach ($pkg in $distPackages) {
    $fileHash = (Get-FileHash -Path $pkg.FullName -Algorithm SHA256).Hash
    $hashLines += "$fileHash  $($pkg.Name)"
}
$hashLines | Out-File -FilePath $sha256Manifest -Encoding utf8 -Force
Write-Host "  -> Manifesto SHA-256 gerado em: $sha256Manifest" -ForegroundColor Green
