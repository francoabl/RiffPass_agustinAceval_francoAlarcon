param(
    [Parameter(Mandatory=$true)][string]$CognitoPoolArn,
    [Parameter(Mandatory=$true)][string]$VpcLinkId,
    [Parameter(Mandatory=$true)][string]$AlbArn,
    [Parameter(Mandatory=$true)][string]$AlbDns,
    [string]$CorsOrigin = "http://localhost:5173"
)
$ErrorActionPreference = "Stop"
if ($CognitoPoolArn -notmatch '^arn:aws:cognito-idp:[a-z0-9-]+:[0-9]{12}:userpool/[A-Za-z0-9_-]+$') { throw "ARN Cognito inválido" }
if ($AlbArn -notmatch '^arn:aws:elasticloadbalancing:[a-z0-9-]+:[0-9]{12}:loadbalancer/app/[A-Za-z0-9/-]+$') { throw "ARN ALB inválido" }
if ($VpcLinkId -notmatch '^[a-z0-9]+$') { throw "VPC Link ID inválido" }
if ($AlbDns -notmatch '^[a-zA-Z0-9.-]+\.elb\.amazonaws\.com$') { throw "Usa el DNS del ALB, sin http:// ni rutas" }
if ($CorsOrigin -notmatch '^https?://[a-zA-Z0-9.-]+(:[0-9]+)?$') { throw "Origen CORS inválido; no incluyas una ruta ni / final" }
$repoDir = Split-Path -Parent $PSScriptRoot
$template = Get-Content -Raw -LiteralPath (Join-Path $repoDir "deploy/api-gateway.template.json")
$result = $template.Replace("__COGNITO_POOL_ARN__", $CognitoPoolArn).Replace("__VPC_LINK_ID__", $VpcLinkId).Replace("__ALB_ARN__", $AlbArn).Replace("__ALB_DNS__", $AlbDns).Replace("__CORS_ORIGIN__", $CorsOrigin)
$result | ConvertFrom-Json | Out-Null
$outputFile = Join-Path $repoDir "deploy/api-gateway.generated.json"
[System.IO.File]::WriteAllText($outputFile, $result, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "Archivo listo para importar en API Gateway: $outputFile"
