param(
    [string]$CasePath = "",
    [string]$SecondaryCasePath = "",
    [string]$ExePath = "dist\IPED-Tools-MCP\IPED-Tools-MCP.exe"
)

# Resolução dinâmica do caso de teste principal
if (-not $CasePath) {
    if ($env:IPED_TEST_CASE_PATH -and (Test-Path $env:IPED_TEST_CASE_PATH)) {
        $CasePath = $env:IPED_TEST_CASE_PATH
    } elseif (Test-Path "local-test.properties") {
        $prop = Get-Content "local-test.properties" | Where-Object { $_ -match "^iped\.test\.case\.path\s*=\s*(.+)$" } | Select-Object -First 1
        if ($prop -match "^iped\.test\.case\.path\s*=\s*(.+)$") {
            $CasePath = $matches[1].Trim()
        }
    }
}

if (-not $CasePath -or -not (Test-Path $CasePath)) {
    Write-Host "ERRO: Caminho do caso do IPED não configurado ou diretório inexistente." -ForegroundColor Red
    Write-Host "Defina o parâmetro -CasePath, configure local-test.properties ou a variável IPED_TEST_CASE_PATH." -ForegroundColor Yellow
    exit 1
}
$CasePath = (Resolve-Path $CasePath).Path

# A versao esperada vem somente do pom.xml
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "version.ps1")
$ExpectedVersion = Get-PomVersion -ProjectRoot $projectRoot

# Resolução dinâmica do caso secundário para teste de troca (open_case)
if (-not $SecondaryCasePath) {
    if ($env:IPED_TEST_SECONDARY_CASE_PATH -and (Test-Path $env:IPED_TEST_SECONDARY_CASE_PATH)) {
        $SecondaryCasePath = $env:IPED_TEST_SECONDARY_CASE_PATH
    } elseif (Test-Path "local-test.properties") {
        $prop = Get-Content "local-test.properties" | Where-Object { $_ -match "^iped\.test\.secondary_case\.path\s*=\s*(.+)$" } | Select-Object -First 1
        if ($prop -match "^iped\.test\.secondary_case\.path\s*=\s*(.+)$") {
            $SecondaryCasePath = $matches[1].Trim()
        }
    }
}

if ($SecondaryCasePath -and (Test-Path $SecondaryCasePath)) {
    $SecondaryCasePath = (Resolve-Path $SecondaryCasePath).Path
} else {
    $SecondaryCasePath = $CasePath
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Testando Executavel Nativo Windows: $ExePath" -ForegroundColor Cyan
Write-Host " Caso: $CasePath" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$resolvedExe = (Resolve-Path $ExePath).Path

# A versao e conferida pelo protocolo (initialize e get_server_status): o executavel nao imprime
# nada no terminal, porque o lancador do jpackage nao tem console.

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $resolvedExe
$psi.Arguments = "--stdio"
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $false
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

# O .NET Framework cria o StandardInput com a codificacao do console; se ela for
# UTF-8 com BOM, o BOM vai antes da primeira mensagem e o JSON chega invalido.
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
$proc = [System.Diagnostics.Process]::Start($psi)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$writer = New-Object System.IO.StreamWriter($proc.StandardInput.BaseStream, $utf8NoBom)
$reader = $proc.StandardOutput

$reqId = 1

function Send-RpcRequest([string]$method, [hashtable]$params = @{}) {
    $script:reqId++
    $currentId = $script:reqId

    $payload = @{
        jsonrpc = "2.0"
        id = $currentId
        method = $method
        params = $params
    } | ConvertTo-Json -Depth 10 -Compress

    $writer.WriteLine($payload)
    $writer.Flush()

    $line = $reader.ReadLine()
    return ($line | ConvertFrom-Json)
}

function Send-RpcNotification([string]$method) {
    $payload = @{
        jsonrpc = "2.0"
        method = $method
    } | ConvertTo-Json -Depth 10 -Compress

    $writer.WriteLine($payload)
    $writer.Flush()
}

try {
    Write-Host "[1/10] Testando Handshake initialize..." -NoNewline
    $init = Send-RpcRequest "initialize" @{
        protocolVersion = "2024-11-05"
        capabilities = @{}
        clientInfo = @{ name = "exe-test"; version = "1.0" }
    }
    if ($init.result.serverInfo.name -eq "iped-tools-mcp" -and -not (Get-VersionMismatch -Reported $init.result.serverInfo.version -Expected $ExpectedVersion)) {
        Write-Host (" PASS (versao: " + $init.result.serverInfo.version + ")") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    Send-RpcNotification "notifications/initialized"

    Write-Host "[2/10] Testando tools/list..." -NoNewline
    $toolsList = Send-RpcRequest "tools/list"
    $toolCount = $toolsList.result.tools.Count
    if ($toolCount -ge 25) {
        Write-Host (" PASS (" + $toolCount + " ferramentas)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL (esperava >= 25, obteve $toolCount)") -ForegroundColor Red; exit 1
    }

    # A sessao comeca sem caso: o caso e aberto com open_case
    Write-Host "[2b/10] Testando open_case..." -NoNewline
    $openCall = Send-RpcRequest "tools/call" @{ name = "open_case"; arguments = @{ case_path = $CasePath } }
    $openData = $openCall.result.content[0].text | ConvertFrom-Json
    if ($openData.status -eq "success") {
        Write-Host (" PASS (" + $openData.total_items + " itens)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($openCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    Write-Host "[3/10] Testando get_server_status e list_sources (UTF-8)..." -NoNewline
    $statusCall = Send-RpcRequest "tools/call" @{ name = "get_server_status"; arguments = @{} }
    $statusData = $statusCall.result.content[0].text | ConvertFrom-Json
    $sourcesCall = Send-RpcRequest "tools/call" @{ name = "list_sources"; arguments = @{} }
    $sourcesJson = ($sourcesCall | ConvertTo-Json -Depth 6 -Compress)
    # Marcador de teste com acentuacao UTF-8: incluido e excluido em seguida, deixando o caso como estava
    $expectedBm = "Confer$([char]0x00EA)ncia de Teste"
    $bmUtf8Call = Send-RpcRequest "tools/call" @{ name = "add_to_bookmark"; arguments = @{ bookmark_name = $expectedBm; doc_ids = @(1) } }
    $bmUtf8Text = $bmUtf8Call.result.content[0].text
    $bmUtf8Data = $bmUtf8Text | ConvertFrom-Json
    $bmDelCall = Send-RpcRequest "tools/call" @{ name = "delete_bookmark"; arguments = @{ bookmark_name = $expectedBm } }
    $bmDelData = $bmDelCall.result.content[0].text | ConvertFrom-Json
    if ($statusData.connected -and $statusData.case_open -and -not (Get-VersionMismatch -Reported $statusData.server_version -Expected $ExpectedVersion) -and -not $sourcesJson.Contains([char]0xFFFD) -and $bmUtf8Data.bookmark.name -eq $expectedBm -and -not $bmUtf8Text.Contains([char]0xFFFD) -and $bmDelData.success) {
        Write-Host (" PASS (Versao: " + $statusData.server_version + " | Fonte: " + $statusData.case_source_id + " | UTF-8 integro)") -ForegroundColor Green
    } else {
        Write-Host " FAIL (Status invalido ou corrupcao UTF-8 U+FFFD)" -ForegroundColor Red; exit 1
    }

    Write-Host "[4/10] Testando get_case_summary..." -NoNewline
    $summaryCall = Send-RpcRequest "tools/call" @{ name = "get_case_summary"; arguments = @{} }
    $summaryData = $summaryCall.result.content[0].text | ConvertFrom-Json
    if ($summaryData.total_indexed_items -gt 0) {
        Write-Host (" PASS (" + $summaryData.total_indexed_items + " itens)") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    Write-Host "[5/10] Testando search_documents..." -NoNewline
    $searchCall = Send-RpcRequest "tools/call" @{ name = "search_documents"; arguments = @{ query = "category:whatsapp"; limit = 3 } }
    $searchData = $searchCall.result.content[0].text | ConvertFrom-Json
    if ($searchData.total_found -gt 0) {
        Write-Host (" PASS (" + $searchData.total_found + " mensagens)") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    # 6. Test open_case dynamically switching case
    Write-Host "[6/10] Testando troca dinamica de caso via 'open_case'..." -NoNewline
    $openCall = Send-RpcRequest "tools/call" @{ name = "open_case"; arguments = @{ case_path = $SecondaryCasePath } }
    $openData = $openCall.result.content[0].text | ConvertFrom-Json
    if ($openData.status -eq "success") {
        # Check summary of new case
        $summary2 = Send-RpcRequest "tools/call" @{ name = "get_case_summary"; arguments = @{} }
        $summaryData2 = $summary2.result.content[0].text | ConvertFrom-Json
        Write-Host (" PASS (Novo caso ativo: " + $summaryData2.case_source_id + ", " + $summaryData2.total_indexed_items + " itens)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL (" + $openData.message + ")") -ForegroundColor Red; exit 1
    }

    # 7. Test list_folder_contents
    Write-Host "[7/10] Testando list_folder_contents..." -NoNewline
    $treeCall = Send-RpcRequest "tools/call" @{ name = "list_folder_contents"; arguments = @{ folder_path = "/"; recursive = $false; limit = 10 } }
    $treeData = $treeCall.result.content[0].text | ConvertFrom-Json
    if ($treeData.folder_path -and ($treeData.subdirectories -ne $null -or $treeData.files -ne $null)) {
        Write-Host (" PASS (" + $treeData.returned_subdirectories_count + " subpastas, " + $treeData.returned_files_count + " arquivos)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($treeCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 8. Test set_item_checked
    Write-Host "[8/10] Testando set_item_checked..." -NoNewline
    $triageCall = Send-RpcRequest "tools/call" @{ name = "set_item_checked"; arguments = @{ item_id = 1; checked = $true } }
    $triageData = $triageCall.result.content[0].text | ConvertFrom-Json
    if ($triageData.success -and $triageData.checked -eq $true) {
        Send-RpcRequest "tools/call" @{ name = "set_item_checked"; arguments = @{ item_id = 1; checked = $false } } | Out-Null
        Write-Host (" PASS (Item 1 marcado e desmarcado com sucesso)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($triageCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 9. Test list_properties
    Write-Host "[9/10] Testando list_properties..." -NoNewline
    $groupsCall = Send-RpcRequest "tools/call" @{ name = "list_properties"; arguments = @{} }
    $groupsData = $groupsCall.result.content[0].text | ConvertFrom-Json
    if ($groupsData.groups -and $groupsData.total_properties -gt 0) {
        Write-Host (" PASS (" + $groupsData.groups.Count + " grupos de propriedades no caso)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($groupsCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 10. Test get_item_thumbnail
    Write-Host "[10/10] Testando get_item_thumbnail (item 1)..." -NoNewline
    $thumbCall = Send-RpcRequest "tools/call" @{ name = "get_item_thumbnail"; arguments = @{ item_id = 1; max_dimension = 256 } }
    $contentBlocks = $thumbCall.result.content
    if ($contentBlocks -and $contentBlocks.Count -ge 1) {
        $hasImg = $contentBlocks | Where-Object { $_.type -eq "image" }
        $hasText = $contentBlocks | Where-Object { $_.type -eq "text" }
        Write-Host (" PASS (Blocos: " + $contentBlocks.Count + " | Texto: " + ($hasText -ne $null) + " | Imagem: " + ($hasImg -ne $null) + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($thumbCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " EXECUTAVEL NATIVO WINDOWS (.EXE) TOTALMENTE FUNCIONAL!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green

} finally {
    if ($proc -and !$proc.HasExited) {
        $proc.Kill()
    }
}
