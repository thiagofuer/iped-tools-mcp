param(
    [string]$CasePath = "",
    [string]$IpedPythonDir = "",
    [string]$ForeignPythonHome = "",
    [string]$ExePath = "",
    [int]$TimeoutSeconds = 120
)

# Regression test: opening a case must not depend on the Python installed on the machine.
# Starts the packaged executable in STDIO mode with an environment that pairs the JEP library of an
# IPED Python installation with a PYTHONHOME of another Python version. Before the fix, the IPED
# configuration step hung forever while starting the embedded Python. The test passes when
# get_server_status reports the case open and the log shows the end of that step.
# Machine paths come from parameters or local-test.properties; none is kept in this script.

$projectRoot = Split-Path -Parent $PSScriptRoot

function Get-LocalProperty([string]$key) {
    $file = Join-Path $projectRoot "local-test.properties"
    if (-not (Test-Path $file)) { return "" }
    $line = Get-Content $file -Encoding UTF8 | Where-Object { $_ -match ("^" + [regex]::Escape($key) + "\s*=\s*(.+)$") } | Select-Object -First 1
    if ($line -and $line -match "=\s*(.+)$") { return $matches[1].Trim() }
    return ""
}

if (-not $CasePath) { $CasePath = $env:IPED_TEST_CASE_PATH }
if (-not $CasePath) { $CasePath = Get-LocalProperty "iped.test.case.path" }
if (-not $IpedPythonDir) { $IpedPythonDir = Get-LocalProperty "iped.test.python.iped_dir" }
if (-not $ForeignPythonHome) { $ForeignPythonHome = Get-LocalProperty "iped.test.python.foreign_home" }
if (-not $ExePath) { $ExePath = Join-Path $projectRoot "dist\IPED-Tools-MCP\IPED-Tools-MCP.exe" }

if (-not $CasePath -or -not (Test-Path $CasePath)) {
    Write-Host "SKIP: caso de teste nao configurado (iped.test.case.path)." -ForegroundColor Yellow; exit 0
}
$jepDir = if ($IpedPythonDir) { Join-Path $IpedPythonDir "Lib\site-packages\jep" } else { "" }
if (-not $IpedPythonDir -or -not (Test-Path (Join-Path $jepDir "jep.dll"))) {
    Write-Host "SKIP: pasta Python do IPED com jep.dll nao configurada (iped.test.python.iped_dir)." -ForegroundColor Yellow; exit 0
}
if (-not $ForeignPythonHome -or -not (Test-Path $ForeignPythonHome)) {
    Write-Host "SKIP: PYTHONHOME de outra versao nao configurado (iped.test.python.foreign_home)." -ForegroundColor Yellow; exit 0
}
if (-not (Test-Path $ExePath)) {
    Write-Host "ERRO: executavel nao encontrado: $ExePath (rode scripts\package_app.ps1)" -ForegroundColor Red; exit 1
}
$CasePath = (Resolve-Path $CasePath).Path

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Teste de isolamento do Python - IPED Tools MCP" -ForegroundColor Cyan
Write-Host " PATH com: $IpedPythonDir (+ jep)" -ForegroundColor Cyan
Write-Host " PYTHONHOME: $ForeignPythonHome" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $ExePath
$psi.Arguments = "--stdio"
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.StandardOutputEncoding = [Text.Encoding]::UTF8
$psi.StandardErrorEncoding = [Text.Encoding]::UTF8
$psi.EnvironmentVariables["PATH"] = "$IpedPythonDir;$jepDir;" + $env:PATH
$psi.EnvironmentVariables["PYTHONHOME"] = $ForeignPythonHome

[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
$proc = [Diagnostics.Process]::Start($psi)
$errTask = $proc.StandardError.ReadToEndAsync()
$writer = New-Object System.IO.StreamWriter($proc.StandardInput.BaseStream, (New-Object System.Text.UTF8Encoding($false)))

function Send($obj) { $writer.WriteLine(($obj | ConvertTo-Json -Depth 6 -Compress)); $writer.Flush() }
function Read-Response([int]$id, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ($true) {
        $left = [int]($deadline - (Get-Date)).TotalMilliseconds
        if ($left -le 0) { return $null }
        $task = $proc.StandardOutput.ReadLineAsync()
        if (-not $task.Wait($left)) { return $null }
        $line = $task.Result
        if ($null -eq $line) { return $null }
        try { $msg = $line | ConvertFrom-Json } catch { continue }
        if ($msg.id -eq $id) { return $msg }
    }
}

$failed = $false
try { do {
    Write-Host "[1/3] initialize..." -NoNewline
    Send @{ jsonrpc = "2.0"; id = 1; method = "initialize"; params = @{ protocolVersion = "2024-11-05"; capabilities = @{}; clientInfo = @{ name = "python-isolation-test"; version = "1" } } }
    if (Read-Response 1 30) { Write-Host " PASS" -ForegroundColor Green } else { Write-Host " FAIL (sem resposta)" -ForegroundColor Red; $failed = $true; break }
    Send @{ jsonrpc = "2.0"; method = "notifications/initialized" }

    Write-Host "[2/3] open_case e get_server_status (limite de $TimeoutSeconds s)..." -NoNewline
    Send @{ jsonrpc = "2.0"; id = 2; method = "tools/call"; params = @{ name = "open_case"; arguments = @{ case_path = $CasePath } } }
    if (-not (Read-Response 2 $TimeoutSeconds)) {
        Write-Host " FAIL (tempo limite: a abertura do caso travou)" -ForegroundColor Red; $failed = $true; break
    }
    Send @{ jsonrpc = "2.0"; id = 3; method = "tools/call"; params = @{ name = "get_server_status"; arguments = @{} } }
    $status = Read-Response 3 30
    if (-not $status) {
        Write-Host " FAIL (tempo limite: a abertura do caso travou)" -ForegroundColor Red; $failed = $true; break
    }
    $json = $status.result.content[0].text | ConvertFrom-Json
    if ($json.case_open -eq $true) { Write-Host " PASS (case_open: true)" -ForegroundColor Green }
    else { Write-Host (" FAIL (case_open: " + $json.case_open + ", last_error: " + ($json.last_error | ConvertTo-Json -Compress) + ")") -ForegroundColor Red; $failed = $true; break }

    Write-Host "[3/3] log com o fim da etapa de configuracoes..." -NoNewline
    # The log also goes to stderr. The launcher and the JVM have different PIDs, so the stderr of this process is
    # read instead of filtering the shared log file.
    $writer.Close()
    if (-not $proc.WaitForExit(20000)) { $proc.Kill() }
    $null = $errTask.Wait(5000)
    $lines = if ($errTask.IsCompleted) { $errTask.Result -split "`r?`n" } else { @() }
    $ended = $lines | Where-Object { $_ -match "\[abertura do caso\] fim: configura" }
    $disabled = $lines | Where-Object { $_ -match "Python embutido do IPED desativado" }
    if ($ended -and $disabled) { Write-Host " PASS" -ForegroundColor Green }
    else { Write-Host " FAIL (fim da etapa: $([bool]$ended), aviso de Python desativado: $([bool]$disabled))" -ForegroundColor Red; $failed = $true }
} while ($false) } finally {
    try { $writer.Close() } catch {}
    if (-not $proc.WaitForExit(20000)) { $proc.Kill() }
}

if ($failed) {
    $null = $errTask.Wait(3000)
    Write-Host "--- stderr (ultimas linhas) ---" -ForegroundColor Yellow
    if ($errTask.IsCompleted) { ($errTask.Result -split "`r?`n") | Select-Object -Last 15 | ForEach-Object { Write-Host $_ } }
    exit 1
}
Write-Host "Teste de isolamento do Python: PASS" -ForegroundColor Green
exit 0
