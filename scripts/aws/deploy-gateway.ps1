param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("gateway-1", "gateway-2", "gateway-3")]
    [string]$NodeId
)

$ErrorActionPreference = "Stop"

$Region = "ap-south-1"
$EcrRepository = "distributed-traffic-control"

$AccountId = aws sts get-caller-identity `
    --query Account `
    --output text

$EcrRegistry = "$AccountId.dkr.ecr.$Region.amazonaws.com"

$RedisHost = aws elasticache describe-replication-groups `
    --replication-group-id distributed-traffic-control-redis `
    --query "ReplicationGroups[0].NodeGroups[0].PrimaryEndpoint.Address" `
    --output text

if ([string]::IsNullOrWhiteSpace($RedisHost)) {
    throw "Unable to resolve the ElastiCache Redis primary endpoint."
}

$BootstrapFile = Join-Path $PSScriptRoot "ec2-gateway-user-data.sh"
$OutputFile = Join-Path $PSScriptRoot "generated-$NodeId-user-data.sh"

$Bootstrap = Get-Content $BootstrapFile -Raw

$EnvHeader = @"
export NODE_ID="$NodeId"
export AWS_REGION="$Region"
export ECR_REGISTRY="$EcrRegistry"
export ECR_REPOSITORY="$EcrRepository"
export REDIS_HOST="$RedisHost"
export REDIS_PORT="6379"
export LEASE_CAPACITY="10"

"@

# Keep the shebang as the first line of the final user-data script.
$Shebang = "#!/bin/bash`n"

$Body = $Bootstrap -replace '^\s*#!/bin/bash\s*', ''

$Shebang + $EnvHeader + $Body |
    Set-Content $OutputFile -NoNewline

Write-Host ""
Write-Host "Gateway user-data generated successfully."
Write-Host "Node ID:        $NodeId"
Write-Host "AWS Region:     $Region"
Write-Host "ECR Registry:   $EcrRegistry"
Write-Host "ECR Repository: $EcrRepository"
Write-Host "Redis Host:     $RedisHost"
Write-Host "Output File:    $OutputFile"
Write-Host ""
