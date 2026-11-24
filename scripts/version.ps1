# Leitura da versao do projeto. A versao e declarada somente no pom.xml;
# os scripts de empacotamento e de teste carregam este arquivo com
#   . (Join-Path $PSScriptRoot "version.ps1")
# e nunca repetem o numero da versao.

function Get-PomVersion {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)

    $pomPath = Join-Path $ProjectRoot "pom.xml"
    try {
        $pomXml = [xml](Get-Content -Raw -Encoding UTF8 $pomPath)
        $version = "$($pomXml.project.version)".Trim()
    } catch {
        throw "Nao foi possivel ler a versao de $pomPath : $($_.Exception.Message)"
    }
    if (-not ($version -match '^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$')) {
        throw "Versao invalida em ${pomPath}: '$version'. Esperado SemVer (ex.: X.Y.Z ou X.Y.Z-SNAPSHOT)."
    }
    return $version
}

# jpackage exige versao estritamente numerica, sem sufixos como -SNAPSHOT.
function Get-NumericVersion {
    param([Parameter(Mandatory = $true)][string]$Version)
    return ($Version -replace '-.*$', '')
}

# Compara a versao informada pela aplicacao com a do pom.xml.
# Retorna $null quando confere, ou a mensagem de falha.
function Get-VersionMismatch {
    param(
        [string]$Reported,
        [Parameter(Mandatory = $true)][string]$Expected
    )
    if ($Reported -eq "dev") {
        return "build sem filtragem do Maven (versao 'dev'); gere o jar com 'mvn clean package'"
    }
    if ($Reported -ne $Expected) {
        return "esperava v$Expected (pom.xml), obteve '$Reported'"
    }
    return $null
}

function Get-RunnerJarPath {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [Parameter(Mandatory = $true)][string]$Version
    )
    $jar = Join-Path $ProjectRoot "target\iped-tools-mcp-$Version-runner.jar"
    if (-not (Test-Path $jar)) {
        throw "Runner JAR da versao $Version nao encontrado ($jar). Execute 'mvn -DskipTests clean package' antes."
    }
    return $jar
}
