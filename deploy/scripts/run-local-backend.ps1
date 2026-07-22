param(
    [string]$EnvironmentFile = "",
    [switch]$ValidateOnly
)

$scriptDirectory = Split-Path -Parent $PSCommandPath
$repositoryRoot = (Resolve-Path (Join-Path $scriptDirectory "..\..")).Path
$resolvedEnvironmentFile = if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    Join-Path $repositoryRoot ".env"
} else {
    (Resolve-Path -LiteralPath $EnvironmentFile).Path
}

if (-not (Test-Path -LiteralPath $resolvedEnvironmentFile)) {
    throw "Environment file not found: $resolvedEnvironmentFile"
}

$loadedVariables = 0
foreach ($environmentLine in Get-Content -LiteralPath $resolvedEnvironmentFile) {
    $trimmedLine = $environmentLine.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmedLine) -or $trimmedLine.StartsWith("#")) {
        continue
    }

    $separatorIndex = $trimmedLine.IndexOf("=")
    if ($separatorIndex -lt 1) {
        continue
    }

    $variableName = $trimmedLine.Substring(0, $separatorIndex).Trim()
    if ($variableName -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
        continue
    }

    $variableValue = $trimmedLine.Substring($separatorIndex + 1).Trim()
    if ($variableValue.Length -ge 2) {
        $firstCharacter = $variableValue[0]
        $lastCharacter = $variableValue[$variableValue.Length - 1]
        $isDoubleQuoted = $firstCharacter -eq [char]34 -and $lastCharacter -eq [char]34
        $isSingleQuoted = $firstCharacter -eq [char]39 -and $lastCharacter -eq [char]39
        if ($isDoubleQuoted -or $isSingleQuoted) {
            $variableValue = $variableValue.Substring(1, $variableValue.Length - 2)
        }
    }

    [Environment]::SetEnvironmentVariable(
        $variableName,
        $variableValue,
        [EnvironmentVariableTarget]::Process
    )
    $loadedVariables++
}

Write-Host "Loaded $loadedVariables environment variables from $resolvedEnvironmentFile."
$configuredRuntime = [Environment]::GetEnvironmentVariable("SAUTI_TEST_VOICE_RUNTIME", "Process")
if ($configuredRuntime -eq "vapi") {
    $configuredVapiPublicKey = [Environment]::GetEnvironmentVariable("VAPI_PUBLIC_KEY", "Process")
    if ([string]::IsNullOrWhiteSpace($configuredVapiPublicKey)) {
        throw "SAUTI_TEST_VOICE_RUNTIME is vapi but VAPI_PUBLIC_KEY is empty in the environment file."
    }
    Write-Host "Vapi test-call configuration is present. Secret values were not printed."
}

if ($ValidateOnly) {
    exit 0
}

Push-Location $repositoryRoot
try {
    & .\gradlew.bat :backend:bootRun
    $gradleExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $gradleExitCode
