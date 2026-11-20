$ErrorActionPreference = "Stop"

if ($env:EVENT_NAME -eq "workflow_dispatch" -or -not $env:BASE_SHA -or $env:BASE_SHA -match '^0+$') {
    [IO.File]::AppendAllText($env:GITHUB_OUTPUT, "gradle_required=true`n", [Text.UTF8Encoding]::new($false))
    exit 0
}

$changedPaths = @(git diff --name-only $env:BASE_SHA $env:HEAD_SHA)
if ($LASTEXITCODE -ne 0) {
    throw "Could not compare base '$($env:BASE_SHA)' with head '$($env:HEAD_SHA)'."
}

$isDocsOnly = $changedPaths.Count -gt 0 -and @($changedPaths | Where-Object {
    $_ -notmatch '(^|/)docs/.*\.md$' -and $_ -notmatch '^[^/]+\.md$'
}).Count -eq 0
$gradleRequired = -not $isDocsOnly
"gradle_required=$($gradleRequired.ToString().ToLowerInvariant())" |
    ForEach-Object {
        [IO.File]::AppendAllText($env:GITHUB_OUTPUT, "$_`n", [Text.UTF8Encoding]::new($false))
    }
