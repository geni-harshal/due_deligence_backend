param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)][string]$Email,
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][long]$ProductId,
    [Parameter(Mandatory = $true)][string]$CompanyName,
    [Parameter(Mandatory = $true)][string]$Cin,
    [string]$EntityType = "Company",
    [int]$CreditPollMaxAttempts = 40,
    [int]$PdfPollMaxAttempts = 40,
    [int]$PollIntervalSeconds = 5,
    [string]$OutputDir = ".\\tmp"
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$text) {
    Write-Host "`n=== $text ===" -ForegroundColor Cyan
}

function Invoke-JsonRequest {
    param(
        [ValidateSet("GET", "POST", "PUT", "DELETE")][string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        $Body = $null
    )

    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 30
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -ContentType "application/json" -Body $json
    }

    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers
}

Write-Step "1) Authenticate user"
$loginBody = @{
    email = $Email
    password = $Password
}
$login = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/auth/login" -Body $loginBody
if (-not $login.token) {
    throw "Login failed: no token returned"
}
$token = $login.token
$authHeaders = @{ Authorization = "Bearer $token" }
Write-Host "Logged in as: $($login.user.email) | role: $($login.user.role)"

Write-Step "2) Create DDR order"
$orderBody = @{
    productId = $ProductId
    entityType = $EntityType
    selectedCompany = @{
        companyName = $CompanyName
        cin = $Cin
        status = "Active"
        companyType = $EntityType
    }
}
$order = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/client/orders" -Headers $authHeaders -Body $orderBody
if (-not $order.id) {
    throw "Order creation failed: missing order id"
}
$orderId = $order.id
Write-Host "Created orderId=$orderId orderNumber=$($order.orderNumber) status=$($order.status)"

Write-Step "3) Trigger async credit report generation"
$null = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/operations/orders/$orderId/generate-credit-report" -Headers $authHeaders
Write-Host "Credit report trigger accepted."

Write-Step "4) Poll until credit report is ready"
$creditReady = $false
$creditPayload = $null
for ($i = 1; $i -le $CreditPollMaxAttempts; $i++) {
    try {
        $creditPayload = Invoke-JsonRequest -Method GET -Url "$BaseUrl/api/operations/orders/$orderId/credit-report" -Headers $authHeaders
        if ($creditPayload -and $creditPayload.status -eq "GENERATED") {
            $creditReady = $true
            Write-Host "Credit report ready on attempt $i"
            break
        }
    } catch {
        # report may not exist yet
    }

    Write-Host "Attempt $i/$CreditPollMaxAttempts: credit report not ready yet..."
    Start-Sleep -Seconds $PollIntervalSeconds
}
if (-not $creditReady) {
    throw "Credit report was not generated in time"
}

Write-Step "5) Trigger backend PDF generation"
$null = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/operations/orders/$orderId/generate-pdf" -Headers $authHeaders
Write-Host "PDF generation trigger accepted."

Write-Step "6) Poll until PDF is ready"
$pdfReady = $false
$pdfStatus = $null
for ($i = 1; $i -le $PdfPollMaxAttempts; $i++) {
    $pdfStatus = Invoke-JsonRequest -Method GET -Url "$BaseUrl/api/operations/orders/$orderId/pdf-status" -Headers $authHeaders
    if ($pdfStatus.ready -eq $true) {
        $pdfReady = $true
        Write-Host "PDF ready on attempt $i | file=$($pdfStatus.fileName)"
        break
    }

    Write-Host "Attempt $i/$PdfPollMaxAttempts: pdf status=$($pdfStatus.status) orderStatus=$($pdfStatus.orderStatus)"
    Start-Sleep -Seconds $PollIntervalSeconds
}
if (-not $pdfReady) {
    throw "PDF was not generated in time"
}

Write-Step "7) Download and verify PDF"
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}
$fileName = if ($pdfStatus.fileName) { $pdfStatus.fileName } else { "DDR-$orderId.pdf" }
$outFile = Join-Path $OutputDir $fileName

$resp = Invoke-WebRequest -Method GET -Uri "$BaseUrl/api/operations/orders/$orderId/download-pdf" -Headers $authHeaders -OutFile $outFile -PassThru
if (-not (Test-Path $outFile)) {
    throw "Download failed: file not found at $outFile"
}
$bytes = [System.IO.File]::ReadAllBytes($outFile)
if ($bytes.Length -lt 8) {
    throw "Downloaded file is too small to be a valid PDF"
}
$signature = [System.Text.Encoding]::ASCII.GetString($bytes[0..4])
if ($signature -ne "%PDF-") {
    throw "Downloaded file is not a PDF (signature: $signature)"
}

Write-Step "Workflow complete"
Write-Host "orderId         : $orderId"
Write-Host "orderNumber     : $($order.orderNumber)"
Write-Host "creditStatus    : $($creditPayload.status)"
Write-Host "pdfStatus       : $($pdfStatus.status)"
Write-Host "downloadedFile  : $outFile"
Write-Host "fileSize(bytes) : $($bytes.Length)"
