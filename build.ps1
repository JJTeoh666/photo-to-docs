param(
    [string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'),
    [string]$SdkPath = '',
    [string]$JdkPath = ''
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# Keep generated keys and caches inside this project; no global SDK changes are needed.
if ($JdkPath) { $env:JAVA_HOME = $JdkPath }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $studioJava) { $env:JAVA_HOME = $studioJava }
}
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 or newer (Android Studio includes one).' }
$sdkLocation = if ($SdkPath) { $SdkPath } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath $sdkLocation)) { throw 'Install Android SDK 36 in Android Studio or set ANDROID_HOME.' }
$env:ANDROID_HOME = $sdkLocation
$env:ANDROID_USER_HOME = Join-Path $PSScriptRoot '.android'
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$readOnlyCache = Join-Path $env:USERPROFILE '.gradle\caches'
if (Test-Path -LiteralPath (Join-Path $readOnlyCache 'modules-2')) { $env:GRADLE_RO_DEP_CACHE = $readOnlyCache }
$escapedSdk = $sdkLocation.Replace('\', '\\').Replace(':', '\:')
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'local.properties'), "sdk.dir=$escapedSdk`n")
& (Join-Path $PSScriptRoot 'gradlew.bat') @Tasks '--console=plain'
exit $LASTEXITCODE
