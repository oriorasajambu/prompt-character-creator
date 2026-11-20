param(
    [Parameter(Mandatory = $true)]
    [string] $AppImagePath
)

$ErrorActionPreference = "Stop"
$appImage = (Resolve-Path -LiteralPath $AppImagePath).Path
$executable = Join-Path $appImage "PromptGenerator.exe"
$charactersFile = Join-Path $appImage "characters.txt"
$outputDirectory = Join-Path $appImage "generated"

foreach ($requiredFile in @($executable, $charactersFile, (Join-Path $appImage "templates.json"))) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Packaged file is missing: $requiredFile"
    }
}

$originalPath = $env:PATH
$env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
try {
    Push-Location $appImage
    try {
        & $executable --help *> $null
        if ($LASTEXITCODE -ne 0) { throw "Packaged executable --help failed." }

        & $executable --overwrite --output-dir generated
        if ($LASTEXITCODE -ne 0) { throw "Packaged prompt generation failed." }

        $prompts = [IO.File]::ReadAllLines((Join-Path $outputDirectory "classic.txt"))
        $expectedCount = @(Get-Content -LiteralPath $charactersFile | Where-Object {
            $trimmed = $_.Trim()
            $trimmed -and -not $trimmed.StartsWith("#")
        }).Count
        if ($prompts.Count -ne $expectedCount) {
            throw "Expected $expectedCount generated prompts, got $($prompts.Count)."
        }
        if (-not ($prompts | Where-Object { $_ -match '\(boa hancock:1\.3\)' })) {
            throw "Packaged prompt output did not substitute the sample character."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:PATH = $originalPath
}

Write-Output "Packaged EXE smoke test passed ($expectedCount generated prompts, no system Java on PATH)."
