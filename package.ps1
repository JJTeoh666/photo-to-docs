$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Run build.ps1 successfully before packaging.' }
$dist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $dist 'PhotoToDocs-debug.apk')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination $dist
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'VERIFICATION.md') -Destination $dist
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'THIRD_PARTY_NOTICES.md') -Destination $dist
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'RELEASE_NOTES.md') -Destination $dist

# Only include an explicit source allow-list. Never package keys, caches or local configuration.
$sourceFolders = @('app\src', 'gradle', 'web\src', 'web\test', 'web\vendor-sources', 'design-system')
$sourceFiles = @('.gitignore','.gitattributes','settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat',
    'app\build.gradle.kts','build.ps1','package.ps1','README.md','VERIFICATION.md','THIRD_PARTY_NOTICES.md','RELEASE_NOTES.md',
    'web\.gitignore','web\package.json','web\package-lock.json','web\build.mjs')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = Join-Path $dist 'PhotoToDocs-source.zip'
$zipStream = [IO.File]::Open($zipPath, [IO.FileMode]::Create)
$archive = [IO.Compression.ZipArchive]::new($zipStream, [IO.Compression.ZipArchiveMode]::Create)
try {
    $files = @($sourceFolders | ForEach-Object { Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot $_) -Recurse -File })
    $files += @($sourceFiles | ForEach-Object { Get-Item -LiteralPath (Join-Path $PSScriptRoot $_) })
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($PSScriptRoot.Length + 1).Replace('\', '/')
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative, [IO.Compression.CompressionLevel]::Optimal)
    }
} finally { $archive.Dispose(); $zipStream.Dispose() }

$examples = Join-Path $dist 'examples'
New-Item -ItemType Directory -Force -Path $examples | Out-Null
foreach ($name in @('sample.docx','sample.tex','sample-source.md')) {
    $file = Join-Path $PSScriptRoot "verification\$name"
    if (Test-Path -LiteralPath $file) { Copy-Item -LiteralPath $file -Destination $examples }
}
$screenshots = Join-Path $dist 'screenshots'
New-Item -ItemType Directory -Force -Path $screenshots | Out-Null
foreach ($name in @('library.png','editor.png','preview.png','settings.png','library-ocean.png','library-graphite.png','settings-ocean.png','settings-graphite.png','preview-graphite.png')) {
    $file = Join-Path $PSScriptRoot "verification\$name"
    if (Test-Path -LiteralPath $file) { Copy-Item -LiteralPath $file -Destination $screenshots }
}
Get-ChildItem -LiteralPath $dist -File | Where-Object Extension -In @('.apk','.zip') | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($_.Name)"
} | Set-Content -LiteralPath (Join-Path $dist 'SHA256SUMS.txt')
Get-ChildItem -LiteralPath $dist -File | Select-Object Name,Length
