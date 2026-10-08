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

# Teste prévio: flag --version
Write-Host "[0/10] Testando flag '--version' no executavel..." -NoNewline
$verOutput = (& $resolvedExe --version | Out-String)
if ($verOutput -match "IPED Tools MCP v1\.0\.0" -and $verOutput -match "ipedtools\.com\.br") {
    $firstLine = ($verOutput -split "`r?`n")[0]
    Write-Host (" PASS (" + $firstLine + ")") -ForegroundColor Green
} else {
    Write-Host " FAIL: $verOutput" -ForegroundColor Red; exit 1
}

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $resolvedExe
$psi.Arguments = "--stdio --case `"$CasePath`""
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $false
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

$proc = [System.Diagnostics.Process]::Start($psi)
$writer = $proc.StandardInput
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
    if ($init.result.serverInfo.name -eq "iped-tools-mcp" -and $init.result.serverInfo.version -like "1.0.0*") {
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

    Write-Host "[3/10] Testando get_server_status..." -NoNewline
    $statusCall = Send-RpcRequest "tools/call" @{ name = "get_server_status"; arguments = @{} }
    $statusData = $statusCall.result.content[0].text | ConvertFrom-Json
    if ($statusData.connected -and $statusData.case_open -and $statusData.server_version -like "1.0.0*") {
        Write-Host (" PASS (Versao: " + $statusData.server_version + " | Fonte: " + $statusData.case_source_id + ")") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
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

    # 9. Test list_ai_filters
    Write-Host "[9/10] Testando list_ai_filters..." -NoNewline
    $filtersCall = Send-RpcRequest "tools/call" @{ name = "list_ai_filters"; arguments = @{} }
    $filtersData = $filtersCall.result.content[0].text | ConvertFrom-Json
    if ($filtersData.filters -and $filtersData.total_filters -ge 7) {
        Write-Host (" PASS (" + $filtersData.total_filters + " filtros de IA disponiveis)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($filtersCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
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
