param(
    [string]$BaseUrl = $env:TRAFFIC_CONTROL_BASE_URL
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = "http://localhost:8081"
}

$BaseUrl = $BaseUrl.TrimEnd("/")

function Fail-SmokeTest {
    param([string]$Message)

    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Invoke-SmokeRequest {
    param(
        [string]$Name,
        [string]$Uri,
        [string]$Method,
        [string]$Body
    )

    try {
        $requestParams = @{
            Uri             = $Uri
            Method          = $Method
            TimeoutSec      = 10
            ErrorAction     = "Stop"
            UseBasicParsing = $true
        }

        if ($Method -eq "POST") {
            $requestParams["ContentType"] = "application/json"
            $requestParams["Body"] = $Body
        }

        $response = Invoke-WebRequest @requestParams
        $statusCode = [int]$response.StatusCode
        $content = $response.Content

        # Handle byte-array response content in Windows PowerShell 5.1.
        if ($content -is [byte[]]) {
            $content = [System.Text.Encoding]::UTF8.GetString($content)
        }
        else {
            $content = [string]$content
        }
    }
    catch {
        $statusCode = $null

        if ($_.Exception.Response) {
            try {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            catch {
                $statusCode = $null
            }
        }

        if ($null -ne $statusCode) {
            Fail-SmokeTest "$Name returned HTTP $statusCode."
        }

        Fail-SmokeTest "$Name failed: $($_.Exception.Message)"
    }

    if ($statusCode -ne 200) {
        Fail-SmokeTest "$Name returned HTTP $statusCode."
    }

    Write-Host "[PASS] $Name returned HTTP 200." -ForegroundColor Green

    return $content
}

Write-Host ""
Write-Host "Traffic Control Deployment Smoke Test" -ForegroundColor Cyan
Write-Host "Target: $BaseUrl"
Write-Host ""

# 1. Check gateway health.
$healthContent = Invoke-SmokeRequest `
    -Name "Gateway health check" `
    -Uri "$BaseUrl/actuator/health" `
    -Method "GET" `
    -Body ""

try {
    $healthBody = $healthContent | ConvertFrom-Json -ErrorAction Stop
}
catch {
    Fail-SmokeTest "Health endpoint returned invalid JSON: $healthContent"
}

if ($healthBody.status -ne "UP") {
    Fail-SmokeTest "Expected health status UP, received '$($healthBody.status)'."
}

Write-Host "[PASS] Gateway reports status UP." -ForegroundColor Green

# 2. Submit a representative traffic-evaluation request.
$request = @{
    requestId = "smoke-test-$([guid]::NewGuid().ToString())"
    subject = @{
        subjectId = "smoke-test-user"
        type = "USER"
    }
    resource = "/api/smoke-test"
    requestedAt = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
} | ConvertTo-Json -Depth 5 -Compress

$resultContent = Invoke-SmokeRequest `
    -Name "Traffic evaluation" `
    -Uri "$BaseUrl/api/v1/traffic/evaluate" `
    -Method "POST" `
    -Body $request

try {
    $decision = $resultContent | ConvertFrom-Json -ErrorAction Stop
}
catch {
    Fail-SmokeTest "Traffic evaluation returned invalid JSON: $resultContent"
}

# 3. Validate the response contract.
if ($decision.status -notin @("ALLOWED", "REJECTED")) {
    Fail-SmokeTest "Unexpected or missing decision status."
}

if ([string]::IsNullOrWhiteSpace([string]$decision.reason)) {
    Fail-SmokeTest "Response is missing the reason field."
}

$remainingCapacity = 0L

if (
    $null -eq $decision.remainingCapacity -or
    -not [long]::TryParse(
        [string]$decision.remainingCapacity,
        [ref]$remainingCapacity
    ) -or
    $remainingCapacity -lt 0
) {
    Fail-SmokeTest "remainingCapacity must be a non-negative integer."
}

Write-Host "[PASS] Decision status: $($decision.status)" -ForegroundColor Green
Write-Host "[PASS] Response payload validated." -ForegroundColor Green

Write-Host ""
Write-Host "Smoke test completed successfully." -ForegroundColor Green

exit 0