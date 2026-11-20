param(
    [Parameter(Mandatory = $true)]
    [string] $JdkHome,
    [Parameter(Mandatory = $true)]
    [string] $ProjectDir,
    [Parameter(Mandatory = $true)]
    [string] $Version
)

$ErrorActionPreference = "Stop"

$jpackage = Join-Path $JdkHome "bin\jpackage.exe"
$modules = Join-Path $JdkHome "jmods"
$inputDirectory = Join-Path $ProjectDir "build\install\prompt-generator\lib"
$outputDirectory = Join-Path $ProjectDir "build\windows"
$appImage = Join-Path $outputDirectory "PromptGenerator"
$archive = Join-Path $outputDirectory "PromptGenerator-windows-x64.zip"
$mainJar = "prompt-generator-$Version.jar"

if (-not (Test-Path -LiteralPath $jpackage -PathType Leaf)) {
    throw "JDK 17 jpackage was not found at '$jpackage'."
}
if (-not (Test-Path -LiteralPath $modules -PathType Container)) {
    throw "JDK 17 modules were not found at '$modules'; a full JDK is required."
}
if (-not (Test-Path -LiteralPath (Join-Path $inputDirectory $mainJar) -PathType Leaf)) {
    throw "Application JAR was not found in '$inputDirectory'. Run the Gradle installDist task first."
}

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
if (Test-Path -LiteralPath $appImage) {
    Remove-Item -LiteralPath $appImage -Recurse -Force
}
if (Test-Path -LiteralPath $archive) {
    Remove-Item -LiteralPath $archive -Force
}

& $jpackage `
    --type app-image `
    --name PromptGenerator `
    --app-version $Version `
    --input $inputDirectory `
    --main-jar $mainJar `
    --main-class com.promptgenerator.MainKt `
    --dest $outputDirectory `
    --win-console
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

Copy-Item -LiteralPath (Join-Path $ProjectDir "characters.txt") -Destination $appImage
Copy-Item -LiteralPath (Join-Path $ProjectDir "templates.json") -Destination $appImage
Compress-Archive -Path $appImage -DestinationPath $archive

Write-Output "App-image: $appImage"
Write-Output "ZIP: $archive"
