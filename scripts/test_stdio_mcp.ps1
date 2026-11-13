param(
    [string]$CasePath = "",
    [string]$JavaPath = "C:\Program Files\Java\jdk-21.0.12\bin\java.exe",
    [string]$JarPath = ""
)

# Resolução dinâmica do caso de teste
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

if (-not $JarPath -or -not (Test-Path $JarPath)) {
    $candidates = Get-ChildItem -Path "target" -Filter "iped-tools-mcp-*-runner.jar" -File
    if ($candidates -and $candidates.Count -gt 0) {
        $JarPath = $candidates[0].FullName
    } else {
        $JarPath = "target\iped-tools-mcp-1.0.1-runner.jar"
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Teste Integrado STDIO MCP - IPED Tools MCP" -ForegroundColor Cyan
Write-Host " Caso: $CasePath" -ForegroundColor Cyan
Write-Host " JAR:  $JarPath" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (-not (Test-Path $JavaPath)) {
    $JavaPath = (Get-Command java).Source
}

# Teste prévio: flag --version
Write-Host "[0/26] Testando flag '--version'..." -NoNewline
$verOutput = & $JavaPath -jar $JarPath --version
if ($verOutput -match "IPED Tools MCP v1\.\d+\.\d+" -and $verOutput -match "ipedtools\.com\.br") {
    Write-Host (" PASS (" + $verOutput[0] + ")") -ForegroundColor Green
} else {
    Write-Host " FAIL: $verOutput" -ForegroundColor Red; exit 1
}

$jvmArgs = @(
    "-Dfile.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
    "-Dstdin.encoding=UTF-8",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.math=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "-jar", $JarPath,
    "--stdio", "--case", $CasePath
)

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JavaPath
$psi.Arguments = ($jvmArgs -join " ")
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $false
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true

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
    # 1. Handshake Initialize
    Write-Host "[1/12] Testando Handshake initialize..." -NoNewline
    $init = Send-RpcRequest "initialize" @{
        protocolVersion = "2024-11-05"
        capabilities = @{}
        clientInfo = @{ name = "mcp-test-suite"; version = "1.0" }
    }
    if ($init.result.serverInfo.name -eq "iped-tools-mcp" -and $init.result.serverInfo.version -like "1.0.1*") {
        Write-Host (" PASS (versao: " + $init.result.serverInfo.version + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL (esperava v1.0.1*, obteve " + $init.result.serverInfo.version + ")") -ForegroundColor Red; exit 1
    }

    # 2. Handshake Initialized Notification
    Send-RpcNotification "notifications/initialized"

    # 3. Tools List
    Write-Host "[2/25] Testando tools/list..." -NoNewline
    $toolsList = Send-RpcRequest "tools/list"
    $toolCount = $toolsList.result.tools.Count
    $expectedTools = 26
    $toolNames = $toolsList.result.tools | ForEach-Object { $_.name }
    if ($toolCount -eq $expectedTools -and ($toolNames -contains "get_contact_relationship") -and ($toolNames -contains "list_properties")) {
        Write-Host (" PASS (" + $toolCount + " ferramentas registradas)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL (" + $toolCount + " ferramentas, esperado " + $expectedTools + " incluindo get_contact_relationship e list_properties)") -ForegroundColor Red; exit 1
    }

    # 4. Call list_properties (group overview)
    Write-Host "[3/25] Testando call list_properties (visao geral)..." -NoNewline
    $groupsCall = Send-RpcRequest "tools/call" @{ name = "list_properties"; arguments = @{} }
    $groupsData = $groupsCall.result.content[0].text | ConvertFrom-Json
    if ($groupsData.groups -and $groupsData.groups.Count -gt 0 -and $groupsData.total_properties -gt 0) {
        Write-Host (" PASS (" + $groupsData.groups.Count + " grupos, " + $groupsData.total_properties + " propriedades)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($groupsCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 5. Call list_properties (group Basic)
    Write-Host "[4/25] Testando call list_properties (grupo Basic)..." -NoNewline
    $basicCall = Send-RpcRequest "tools/call" @{ name = "list_properties"; arguments = @{ group = "Basic" } }
    $basicData = $basicCall.result.content[0].text | ConvertFrom-Json
    if ($basicData.properties -and $basicData.total -gt 0 -and $basicData.properties[0].query_example) {
        Write-Host (" PASS (" + $basicData.total + " propriedades no grupo Basic)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($basicCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 6. Call get_server_status
    Write-Host "[5/15] Testando call get_server_status..." -NoNewline
    $statusCall = Send-RpcRequest "tools/call" @{ name = "get_server_status"; arguments = @{} }
    $statusData = $statusCall.result.content[0].text | ConvertFrom-Json
    if ($statusData.connected -and $statusData.case_open -and $statusData.server_version -like "1.0.1*") {
        Write-Host (" PASS (Versao: " + $statusData.server_version + " | Fonte: " + $statusData.case_source_id + ")") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    # 7. Call get_case_summary
    Write-Host "[6/15] Testando call get_case_summary..." -NoNewline
    $summaryCall = Send-RpcRequest "tools/call" @{ name = "get_case_summary"; arguments = @{} }
    $summaryData = $summaryCall.result.content[0].text | ConvertFrom-Json
    if ($summaryData.total_indexed_items -gt 0) {
        Write-Host (" PASS (" + $summaryData.total_indexed_items + " itens indexados)") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    # 8. Call get_device_and_accounts
    Write-Host "[7/25] Testando call get_device_and_accounts..." -NoNewline
    $accCall = Send-RpcRequest "tools/call" @{ name = "get_device_and_accounts"; arguments = @{} }
    $accText = $accCall.result.content[0].text
    $accData = $accText | ConvertFrom-Json
    $hasOwnerKey = $accText -match '"[a-z_]*owner[a-z_]*"\s*:'
    if ($accData.evidences -and $accData.note -and -not $hasOwnerKey) {
        Write-Host (" PASS (tipo: " + $accData.evidence_type + ", " + $accData.total_evidences + " evidencia(s), sem campos de dono)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($accCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 9. Call search_documents
    Write-Host "[8/15] Testando call search_documents..." -NoNewline
    $searchCall = Send-RpcRequest "tools/call" @{ name = "search_documents"; arguments = @{ query = 'category:"chat messages"'; limit = 5 } }
    $searchData = $searchCall.result.content[0].text | ConvertFrom-Json
    if ($searchData.total_found -gt 0 -and $searchData.items.Count -gt 0) {
        Write-Host (" PASS (" + $searchData.total_found + " encontrados, " + $searchData.items.Count + " retornados)") -ForegroundColor Green
        $sampleDocId = $searchData.items[0].id
    } else {
        $searchCall = Send-RpcRequest "tools/call" @{ name = "search_documents"; arguments = @{ query = "*:*"; limit = 5 } }
        $searchData = $searchCall.result.content[0].text | ConvertFrom-Json
        $sampleDocId = $searchData.items[0].id
        Write-Host (" PASS (Query fallback retornou " + $searchData.total_found + " itens)") -ForegroundColor Green
    }

    # 9b. Call search_documents second page (pagination)
    Write-Host "[8b/25] Testando paginacao de search_documents (offset)..." -NoNewline
    $page1 = (Send-RpcRequest "tools/call" @{ name = "search_documents"; arguments = @{ query = "*:*"; limit = 5 } }).result.content[0].text | ConvertFrom-Json
    $page2 = (Send-RpcRequest "tools/call" @{ name = "search_documents"; arguments = @{ query = "*:*"; limit = 5; offset = $page1.next_offset } }).result.content[0].text | ConvertFrom-Json
    $ids1 = $page1.items | ForEach-Object { $_.id }
    $repeated = $page2.items | Where-Object { $ids1 -contains $_.id }
    if ($page1.has_more -and $page1.next_offset -eq 5 -and $page2.offset -eq 5 -and $page2.items.Count -eq 5 -and -not $repeated -and $page2.total_found -eq $page1.total_found) {
        Write-Host (" PASS (paginas 1 e 2 sem repeticao, total " + $page1.total_found + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($page2 | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 10. Call get_document_metadata
    Write-Host ("[9/15] Testando call get_document_metadata (id: " + $sampleDocId + ")...") -NoNewline
    $metaCall = Send-RpcRequest "tools/call" @{ name = "get_document_metadata"; arguments = @{ doc_ids = @($sampleDocId) } }
    $metaText = $metaCall.result.content[0].text
    $metaData = $metaText | ConvertFrom-Json
    $item = if ($metaData -is [System.Array]) { $metaData[0] } else { $metaData }
    $docName = if ($item.properties.basic) { $item.properties.basic.name } else { $item.properties.name }
    if ($item.id -eq $sampleDocId -and $item.properties -and -not $metaText.Contains([char]0xFFFD)) {
        $blocks = ($item.properties | Get-Member -MemberType NoteProperty).Name -join ", "
        Write-Host (" PASS (Nome: " + $docName + " | Blocos presentes: " + $blocks + ")") -ForegroundColor Green
    } else {
        Write-Host " FAIL (Metadados invalidos ou corrupcao UTF-8 U+FFFD detectada)" -ForegroundColor Red; exit 1
    }

    # 11. Call get_document_text
    Write-Host ("[10/15] Testando call get_document_text (id: " + $sampleDocId + ")...") -NoNewline
    $textCall = Send-RpcRequest "tools/call" @{ name = "get_document_text"; arguments = @{ doc_id = $sampleDocId; offset = 0; max_chars = 300 } }
    $textStr = $textCall.result.content[0].text
    if ($textStr -and $textStr.Length -gt 0) {
        Write-Host (" PASS (" + $textStr.Length + " caracteres lidos)") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    # 12. Call list_sources, list_categories & list_bookmarks
    Write-Host "[11/15] Testando calls list_sources, list_categories e list_bookmarks (UTF-8)..." -NoNewline
    $sourcesCall = Send-RpcRequest "tools/call" @{ name = "list_sources"; arguments = @{} }
    $sourcesJson = ($sourcesCall | ConvertTo-Json -Depth 6 -Compress)
    $catsCall = Send-RpcRequest "tools/call" @{ name = "list_categories"; arguments = @{} }
    $catsCount = $catsCall.result.content.Count
    $bmsCall = Send-RpcRequest "tools/call" @{ name = "list_bookmarks"; arguments = @{} }
    $bmsCount = $bmsCall.result.content.Count
    if ($catsCount -gt 0 -and -not $sourcesJson.Contains([char]0xFFFD)) {
        Write-Host (" PASS (" + $catsCount + " categorias, " + $bmsCount + " marcadores, UTF-8 integro)") -ForegroundColor Green
    } else {
        Write-Host " FAIL (Categorias vazias ou corrupcao UTF-8 U+FFFD em list_sources)" -ForegroundColor Red; exit 1
    }

    # 13. Call add_to_bookmark (com acentuacao UTF-8)
    $expectedBm = "Per$([char]0x00ED)cia IA"
    Write-Host ("[12/15] Testando call add_to_bookmark (UTF-8 '" + $expectedBm + "')...") -NoNewline
    $addBmCall = Send-RpcRequest "tools/call" @{ name = "add_to_bookmark"; arguments = @{ bookmark_name = $expectedBm; doc_ids = @($sampleDocId) } }
    $addBmText = $addBmCall.result.content[0].text
    if ($addBmText -match "Sucesso" -and $addBmText.Contains($expectedBm) -and -not $addBmText.Contains([char]0xFFFD)) {
        Write-Host (" PASS (" + $addBmText + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL (" + $addBmText + ")") -ForegroundColor Red; exit 1
    }

    # 14. Call get_item_relations
    Write-Host ("[13/15] Testando call get_item_relations (id: " + $sampleDocId + ")...") -NoNewline
    $relCall = Send-RpcRequest "tools/call" @{ name = "get_item_relations"; arguments = @{ item_id = [int]$sampleDocId } }
    $relData = $relCall.result.content[0].text | ConvertFrom-Json
    if ($relData.target_item -and $relData.children_count -ge 0 -and $relData.duplicates_count -ge 0) {
        $pInfo = if ($relData.parent) { "Parent ID: " + $relData.parent.id } else { "Root / Sem parent" }
        Write-Host (" PASS (" + $pInfo + " | Filhos: " + $relData.children_count + " | Duplicatas: " + $relData.duplicates_count + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($relCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 15. Call get_timeline
    Write-Host "[14/15] Testando call get_timeline..." -NoNewline
    $timelineCall = Send-RpcRequest "tools/call" @{ name = "get_timeline"; arguments = @{ start_date = "2024-06-01"; end_date = "2024-06-05"; limit = 10 } }
    $timelineData = $timelineCall.result.content[0].text | ConvertFrom-Json
    if ($timelineData.returned_count -eq 0) {
        $timelineCall = Send-RpcRequest "tools/call" @{ name = "get_timeline"; arguments = @{ start_date = "2000-01-01"; end_date = "2030-12-31"; limit = 10 } }
        $timelineData = $timelineCall.result.content[0].text | ConvertFrom-Json
    }
    if ($timelineData.events -ne $null -and $timelineData.returned_count -gt 0) {
        Write-Host (" PASS (" + $timelineData.returned_count + " eventos cronologicos extraidos)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($timelineCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 16. Call get_events_around_time
    Write-Host "[15/17] Testando call get_events_around_time..." -NoNewline
    $sampleTime = if ($timelineData.events -and $timelineData.events.Count -gt 0) { $timelineData.events[0].timestamp } else { "2024-06-02T17:39:14Z" }
    $aroundCall = Send-RpcRequest "tools/call" @{ name = "get_events_around_time"; arguments = @{ target_time = $sampleTime; window_minutes = 60; limit = 10 } }
    $aroundData = $aroundCall.result.content[0].text | ConvertFrom-Json
    if ($aroundData.events -ne $null -and $aroundData.window_minutes -eq 60) {
        Write-Host (" PASS (" + $aroundData.returned_count + " eventos na janela de +/- 60min ao redor de " + $sampleTime + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($aroundCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 17. Call get_top_contacts
    Write-Host "[16/19] Testando call get_top_contacts..." -NoNewline
    $contactsCall = Send-RpcRequest "tools/call" @{ name = "get_top_contacts"; arguments = @{ limit = 10 } }
    $contactsData = $contactsCall.result.content[0].text | ConvertFrom-Json
    $contactFields = @("rank", "identifier", "name", "total_interactions", "messages_count", "calls_count", "incoming_count",
        "outgoing_count", "unknown_direction_count", "channels", "by_channel", "by_month", "active_days",
        "first_interaction", "last_interaction", "evidence_ids")
    $contactsOk = $contactsData.contacts -ne $null -and $contactsData.diagnostics.events_analyzed -ne $null `
        -and $contactsData.truncated -ne $null -and $contactsData.unknown_direction_total -ne $null `
        -and $contactsData.group_activity -ne $null -and $contactsData.returned_count -le 10
    foreach ($c in $contactsData.contacts) {
        $names = ($c | Get-Member -MemberType NoteProperty).Name
        foreach ($f in $contactFields) { if ($names -notcontains $f) { $contactsOk = $false } }
        if ($c.total_interactions -ne ($c.incoming_count + $c.outgoing_count)) { $contactsOk = $false }
        if ($c.evidence_ids.Count -gt 5) { $contactsOk = $false }
    }
    if ($contactsOk) {
        $dir = $contactsData.diagnostics.with_explicit_direction
        Write-Host (" PASS (" + $contactsData.total_contacts_discovered + " contatos, " + $contactsData.returned_count + " no ranking, " +
            $contactsData.diagnostics.events_analyzed + " eventos, direcao explicita " + $dir.percent + "%, unknown " + $contactsData.unknown_direction_total + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($contactsCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 18. Call get_communications_graph (sem e com Mermaid)
    Write-Host "[17/19] Testando call get_communications_graph..." -NoNewline
    $graphCall = Send-RpcRequest "tools/call" @{ name = "get_communications_graph"; arguments = @{ min_interactions = 1; limit_edges = 20 } }
    $graphData = $graphCall.result.content[0].text | ConvertFrom-Json
    $graphNames = ($graphData | Get-Member -MemberType NoteProperty).Name
    $graphOk = $graphData.nodes -ne $null -and $graphData.edges -ne $null -and $graphData.graph_source -eq "lucene-index" `
        -and $graphData.truncated -ne $null -and $graphData.diagnostics.events_analyzed -ne $null `
        -and $graphData.unlinked_events -ne $null -and $graphData.edges.Count -le 20 -and ($graphNames -notcontains "mermaid")
    foreach ($n in $graphData.nodes) {
        if (@("ACCOUNT", "GROUP", "LOCAL_ENDPOINT") -notcontains $n.type) { $graphOk = $false }
        if ($n.id -eq "Dispositivo_Local") { $graphOk = $false }
        if ($n.type -eq "LOCAL_ENDPOINT" -and $n.identity_resolved -ne $false) { $graphOk = $false }
    }
    foreach ($e in $graphData.edges) { if ($e.evidence_ids.Count -gt 5) { $graphOk = $false } }
    $mermaidCall = Send-RpcRequest "tools/call" @{ name = "get_communications_graph"; arguments = @{ min_interactions = 1; limit_edges = 20; include_mermaid = $true } }
    $mermaidData = $mermaidCall.result.content[0].text | ConvertFrom-Json
    if (-not ($mermaidData.mermaid -like "flowchart LR*")) { $graphOk = $false }
    if ($graphOk) {
        Write-Host (" PASS (" + $graphData.total_nodes + " nos, " + $graphData.total_edges + " de " + $graphData.total_eligible_edges +
            " arestas, truncated=" + $graphData.truncated + ", unlinked=" + $graphData.unlinked_events + ", mermaid OK)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($graphCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 18b. Call get_contact_relationship
    Write-Host "[18b/19] Testando call get_contact_relationship..." -NoNewline
    $pair = $graphData.edges | Where-Object { -not $_.source.StartsWith("local:") -and -not $_.target.StartsWith("local:") } | Select-Object -First 1
    if (-not $pair) { $pair = $graphData.edges | Select-Object -First 1 }
    if ($pair) {
        $relA = $pair.source; $relB = $pair.target
        if ($relA.StartsWith("local:")) { $relA = $pair.target; $relB = $graphData.nodes[0].id }
    } else {
        $relA = "zz-inexistente-zz"; $relB = "yy-inexistente-yy"
    }
    $relCallC = Send-RpcRequest "tools/call" @{ name = "get_contact_relationship"; arguments = @{ contact_a = $relA; contact_b = $relB; evidence_limit = 5 } }
    $relDataC = $relCallC.result.content[0].text | ConvertFrom-Json
    $relOk = @("found", "no_relationship", "ambiguous", "not_found", "same_identity") -contains $relDataC.status `
        -and $relDataC.diagnostics -ne $null
    if ($relDataC.status -eq "found") {
        $relOk = $relOk -and $relDataC.by_channel -ne $null -and $relDataC.by_month -ne $null -and $relDataC.evidence_ids.Count -le 5
    }
    if ($relOk) {
        Write-Host (" PASS (" + $relA + " x " + $relB + ": status=" + $relDataC.status + ", interacoes=" + $relDataC.total_interactions + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($relCallC | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 19. Call list_folder_contents
    Write-Host "[18/19] Testando call list_folder_contents..." -NoNewline
    $treeCall = Send-RpcRequest "tools/call" @{ name = "list_folder_contents"; arguments = @{ folder_path = "/"; recursive = $false; limit = 20 } }
    $treeData = $treeCall.result.content[0].text | ConvertFrom-Json
    if ($treeData.folder_path -and ($treeData.subdirectories -ne $null -or $treeData.files -ne $null)) {
        Write-Host (" PASS (Pasta: " + $treeData.folder_path + " | " + $treeData.returned_subdirectories_count + " subpastas, " + $treeData.returned_files_count + " arquivos)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($treeCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 20. Call set_item_checked
    Write-Host "[19/19] Testando call set_item_checked..." -NoNewline
    $triageCheck = Send-RpcRequest "tools/call" @{ name = "set_item_checked"; arguments = @{ item_id = [int]$sampleDocId; checked = $true } }
    $triageData = $triageCheck.result.content[0].text | ConvertFrom-Json
    if ($triageData.success -and $triageData.checked -eq $true) {
        # unmark back
        Send-RpcRequest "tools/call" @{ name = "set_item_checked"; arguments = @{ item_id = [int]$sampleDocId; checked = $false } } | Out-Null
        Write-Host (" PASS (Item " + $sampleDocId + " marcado e desmarcado com sucesso)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($triageCheck | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 21. Call get_item_thumbnail
    Write-Host "[20/25] Testando call get_item_thumbnail..." -NoNewline
    $thumbCall = Send-RpcRequest "tools/call" @{ name = "get_item_thumbnail"; arguments = @{ item_id = [int]$sampleDocId; max_dimension = 256 } }
    $contentBlocks = $thumbCall.result.content
    if ($contentBlocks -and $contentBlocks.Count -ge 1) {
        $hasImg = $contentBlocks | Where-Object { $_.type -eq "image" }
        $hasText = $contentBlocks | Where-Object { $_.type -eq "text" }
        Write-Host (" PASS (Blocos: " + $contentBlocks.Count + " | Texto: " + ($hasText -ne $null) + " | Imagem: " + ($hasImg -ne $null) + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($thumbCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 22. Call search_similar_images
    Write-Host "[21/25] Testando call search_similar_images..." -NoNewline
    $simImgCall = Send-RpcRequest "tools/call" @{ name = "search_similar_images"; arguments = @{ item_id = [int]$sampleDocId; min_score = 1.0; limit = 5 } }
    $simImgData = $simImgCall.result.content[0].text | ConvertFrom-Json
    if ($simImgData.reference_id -eq $sampleDocId) {
        Write-Host (" PASS (Referencia ID: " + $simImgData.reference_id + " | Encontrados: " + $simImgData.total_found + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($simImgCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 23. Call search_similar_faces
    Write-Host "[22/25] Testando call search_similar_faces..." -NoNewline
    $simFaceCall = Send-RpcRequest "tools/call" @{ name = "search_similar_faces"; arguments = @{ item_id = [int]$sampleDocId; min_score = 50.0; limit = 5 } }
    $simFaceData = $simFaceCall.result.content[0].text | ConvertFrom-Json
    if ($simFaceData.reference_id -eq $sampleDocId) {
        Write-Host (" PASS (Referencia ID: " + $simFaceData.reference_id + " | Encontrados: " + $simFaceData.total_found + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($simFaceCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 24. Call search_similar_documents
    Write-Host "[23/25] Testando call search_similar_documents..." -NoNewline
    $simDocCall = Send-RpcRequest "tools/call" @{ name = "search_similar_documents"; arguments = @{ item_id = [int]$sampleDocId; match_percent = 50; limit = 5 } }
    $simDocData = $simDocCall.result.content[0].text | ConvertFrom-Json
    if ($simDocData.reference_id -eq $sampleDocId) {
        Write-Host (" PASS (Referencia ID: " + $simDocData.reference_id + " | Encontrados: " + $simDocData.total_found + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($simDocCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 25. Call list_properties (group AI)
    Write-Host "[24/25] Testando call list_properties (grupo AI)..." -NoNewline
    $aiCall = Send-RpcRequest "tools/call" @{ name = "list_properties"; arguments = @{ group = "AI" } }
    $aiData = $aiCall.result.content[0].text | ConvertFrom-Json
    if (-not $aiData.error -and $aiData.group -eq "AI" -and $aiData.total -ne $null) {
        Write-Host (" PASS (" + $aiData.total + " propriedades de IA no caso)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($aiCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 26. Call describe_property on a property that IPED does not produce
    Write-Host "[25/25] Testando call describe_property (propriedade ausente)..." -NoNewline
    $descCall = Send-RpcRequest "tools/call" @{ name = "describe_property"; arguments = @{ name = "hasWeapon" } }
    $descData = $descCall.result.content[0].text | ConvertFrom-Json
    if ($descData.present -eq $false -and $descData.message) {
        Write-Host (" PASS (hasWeapon reportada como ausente do caso)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($descCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " SUCESSO: Todos os 25 testes do protocolo MCP passaram!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green

} finally {
    if ($proc -and !$proc.HasExited) {
        $proc.Kill()
    }
}
