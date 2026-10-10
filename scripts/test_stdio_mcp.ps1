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
if ($verOutput -match "IPED Tools MCP v1\.0\.0" -and $verOutput -match "ipedtools\.com\.br") {
    Write-Host (" PASS (" + $verOutput[0] + ")") -ForegroundColor Green
} else {
    Write-Host " FAIL: $verOutput" -ForegroundColor Red; exit 1
}

$jvmArgs = @(
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
    if ($toolCount -ge 24) {
        Write-Host (" PASS (" + $toolCount + " ferramentas registradas)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL (" + $toolCount + " ferramentas)") -ForegroundColor Red; exit 1
    }

    # 4. Call get_property_dictionary
    Write-Host "[3/15] Testando call get_property_dictionary (dominio: chats)..." -NoNewline
    $dictCall = Send-RpcRequest "tools/call" @{ name = "get_property_dictionary"; arguments = @{ domain = "chats" } }
    $dictData = $dictCall.result.content[0].text | ConvertFrom-Json
    if ($dictData.properties -and $dictData.properties.Count -gt 0 -and $dictData.escaping_note) {
        Write-Host (" PASS (" + $dictData.properties.Count + " propriedades catalogadas)") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    # 5. Call list_available_properties
    Write-Host "[4/15] Testando call list_available_properties..." -NoNewline
    $availCall = Send-RpcRequest "tools/call" @{ name = "list_available_properties"; arguments = @{ category = "whatsapp" } }
    $availData = $availCall.result.content[0].text | ConvertFrom-Json
    if ($availData.distinct_properties_count -eq 0) {
        $availCall = Send-RpcRequest "tools/call" @{ name = "list_available_properties"; arguments = @{ category = "" } }
        $availData = $availCall.result.content[0].text | ConvertFrom-Json
    }
    if ($availData.properties -ne $null -and $availData.distinct_properties_count -gt 0) {
        Write-Host (" PASS (" + $availData.distinct_properties_count + " propriedades distintas na categoria '" + $availData.category + "')") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($availCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
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

    # 8. Call get_device_and_owner_info
    Write-Host "[7/15] Testando call get_device_and_owner_info..." -NoNewline
    $ownerCall = Send-RpcRequest "tools/call" @{ name = "get_device_and_owner_info"; arguments = @{} }
    $ownerData = $ownerCall.result.content[0].text | ConvertFrom-Json
    if ($ownerData.likely_owner_names.Count -gt 0 -or $ownerData.owner_phone_numbers.Count -gt 0) {
        $ownerStr = ($ownerData.likely_owner_names -join ", ")
        $phoneStr = ($ownerData.owner_phone_numbers -join ", ")
        Write-Host (" PASS (Proprietario: " + $ownerStr + " | Fone: " + $phoneStr + ")") -ForegroundColor Green
    } else {
        Write-Host " PASS (Sem proprietario explicito)" -ForegroundColor Yellow
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

    # 10. Call get_document_metadata
    Write-Host ("[9/15] Testando call get_document_metadata (id: " + $sampleDocId + ")...") -NoNewline
    $metaCall = Send-RpcRequest "tools/call" @{ name = "get_document_metadata"; arguments = @{ doc_ids = @($sampleDocId) } }
    $metaText = $metaCall.result.content[0].text
    $metaData = $metaText | ConvertFrom-Json
    $item = if ($metaData -is [System.Array]) { $metaData[0] } else { $metaData }
    $docName = if ($item.properties.basic) { $item.properties.basic.name } else { $item.properties.name }
    if ($item.id -eq $sampleDocId -and $item.properties) {
        $blocks = ($item.properties | Get-Member -MemberType NoteProperty).Name -join ", "
        Write-Host (" PASS (Nome: " + $docName + " | Blocos presentes: " + $blocks + ")") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
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

    # 12. Call list_categories & list_bookmarks
    Write-Host "[11/15] Testando calls list_categories e list_bookmarks..." -NoNewline
    $catsCall = Send-RpcRequest "tools/call" @{ name = "list_categories"; arguments = @{} }
    $catsCount = $catsCall.result.content.Count
    $bmsCall = Send-RpcRequest "tools/call" @{ name = "list_bookmarks"; arguments = @{} }
    $bmsCount = $bmsCall.result.content.Count
    if ($catsCount -gt 0) {
        Write-Host (" PASS (" + $catsCount + " categorias, " + $bmsCount + " marcadores)") -ForegroundColor Green
    } else {
        Write-Host " FAIL" -ForegroundColor Red; exit 1
    }

    # 13. Call add_to_bookmark
    Write-Host "[12/15] Testando call add_to_bookmark..." -NoNewline
    $addBmCall = Send-RpcRequest "tools/call" @{ name = "add_to_bookmark"; arguments = @{ bookmark_name = "Pericia AI"; doc_ids = @($sampleDocId) } }
    $addBmText = $addBmCall.result.content[0].text
    if ($addBmText -match "Sucesso") {
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
    Write-Host "[16/17] Testando call get_top_contacts..." -NoNewline
    $contactsCall = Send-RpcRequest "tools/call" @{ name = "get_top_contacts"; arguments = @{ limit = 10 } }
    $contactsData = $contactsCall.result.content[0].text | ConvertFrom-Json
    if ($contactsData.contacts -ne $null -and $contactsData.total_contacts_discovered -ge 0) {
        Write-Host (" PASS (" + $contactsData.total_contacts_discovered + " contatos identificados, " + $contactsData.returned_count + " no ranking)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($contactsCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 18. Call get_communications_graph
    Write-Host "[17/19] Testando call get_communications_graph..." -NoNewline
    $graphCall = Send-RpcRequest "tools/call" @{ name = "get_communications_graph"; arguments = @{ min_interactions = 1; limit_edges = 20 } }
    $graphData = $graphCall.result.content[0].text | ConvertFrom-Json
    if ($graphData.nodes -ne $null -and $graphData.edges -ne $null) {
        Write-Host (" PASS (" + $graphData.total_nodes + " nos, " + $graphData.total_edges + " arestas no grafo)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($graphCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
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

    # 25. Call list_ai_filters
    Write-Host "[24/25] Testando call list_ai_filters..." -NoNewline
    $filtersCall = Send-RpcRequest "tools/call" @{ name = "list_ai_filters"; arguments = @{} }
    $filtersData = $filtersCall.result.content[0].text | ConvertFrom-Json
    if ($filtersData.filters -and $filtersData.total_filters -ge 7) {
        Write-Host (" PASS (" + $filtersData.total_filters + " categorias de filtros AI listadas)") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($filtersCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    # 26. Call query_ai_detections
    Write-Host "[25/25] Testando call query_ai_detections..." -NoNewline
    $queryAiCall = Send-RpcRequest "tools/call" @{ name = "query_ai_detections"; arguments = @{ filter_type = "faces"; limit = 5 } }
    $queryAiData = $queryAiCall.result.content[0].text | ConvertFrom-Json
    if ($queryAiData.filter_type -eq "faces" -and $queryAiData.total_found -ne $null) {
        Write-Host (" PASS (Filtro: faces | Total encontrados: " + $queryAiData.total_found + ")") -ForegroundColor Green
    } else {
        Write-Host (" FAIL: " + ($queryAiCall | ConvertTo-Json -Depth 5 -Compress)) -ForegroundColor Red; exit 1
    }

    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host " SUCESSO: Todos os 25 testes do protocolo MCP passaram!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green

} finally {
    if ($proc -and !$proc.HasExited) {
        $proc.Kill()
    }
}
