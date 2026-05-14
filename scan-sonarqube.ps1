param(
    [string]$SonarHostUrl = $env:SONAR_HOST_URL,
    [string]$SonarToken = $env:SONAR_TOKEN
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mavenSettings = Join-Path $root "maven-settings.xml"
$backendServices = @(
    "eureka-server",
    "auth-service",
    "warehouse-service",
    "product-service",
    "purchase-order-service",
    "supplier-service",
    "stock-movement-service",
    "payment-service",
    "alert-service",
    "report-service",
    "admin-server",
    "api-gateway"
)

function Invoke-MavenVerify {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ServiceName
    )

    $serviceDir = Join-Path $root $ServiceName
    $wrapper = Join-Path $serviceDir "mvnw.cmd"
    $maven = Get-Command "mvn" -ErrorAction SilentlyContinue

    if (-not (Test-Path (Join-Path $serviceDir "pom.xml"))) {
        Write-Warning "Skipping $ServiceName because pom.xml was not found."
        return
    }

    Write-Host ""
    Write-Host "=================================================="
    Write-Host "Generating coverage for $ServiceName"
    Write-Host "=================================================="

    if ($maven) {
        $command = $maven.Source
    }
    elseif (Test-Path $wrapper) {
        $command = $wrapper
    }
    else {
        throw "Neither mvn nor mvnw.cmd was available for $ServiceName."
    }
    $arguments = @()
    if (Test-Path $mavenSettings) {
        $arguments += @("-s", $mavenSettings)
    }
    $arguments += @("clean", "verify")

    Push-Location $serviceDir
    try {
        & $command @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$ServiceName verification failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

function Invoke-FrontendCoverage {
    $frontendDir = Join-Path $root "..\\frontend"

    if (-not (Test-Path (Join-Path $frontendDir "package.json"))) {
        Write-Warning "Skipping frontend because package.json was not found."
        return
    }

    Write-Host ""
    Write-Host "=================================================="
    Write-Host "Generating coverage for frontend"
    Write-Host "=================================================="

    Push-Location $frontendDir
    try {
        & npm run test:ci
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend Jest coverage run failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

function Invoke-SonarScanner {
    $maven = Get-Command "mvn" -ErrorAction SilentlyContinue
    if (-not $maven) {
        throw "mvn was not found in PATH. Maven is required to upload the combined SonarQube analysis."
    }

    if (-not $SonarToken) {
        Write-Warning "No Sonar token was provided. Set SONAR_TOKEN or pass -SonarToken."
    }

    Write-Host ""
    Write-Host "=================================================="
    Write-Host "Uploading combined SonarQube analysis"
    Write-Host "=================================================="

    $arguments = @()
    if (Test-Path $mavenSettings) {
        $arguments += @("-s", $mavenSettings)
    }
    $arguments += @(
        "-N",
        "org.sonarsource.scanner.maven:sonar-maven-plugin:5.6.0.6792:sonar"
    )
    if ($SonarHostUrl) {
        $arguments += "-Dsonar.host.url=$SonarHostUrl"
    }
    if ($SonarToken) {
        $arguments += "-Dsonar.token=$SonarToken"
        $arguments += "-Dsonar.login=$SonarToken"
    }

    Push-Location $root
    try {
        & $maven.Source @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Sonar Maven upload failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

foreach ($service in $backendServices) {
    Invoke-MavenVerify -ServiceName $service
}

Invoke-FrontendCoverage
Invoke-SonarScanner
