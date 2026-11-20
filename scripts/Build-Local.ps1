param(
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$GradleDistribution,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$ReadOnlyGradleCache = "$env:USERPROFILE\.gradle\caches",
    [switch]$Offline,
    [string[]]$Tasks = @(':app:assembleDebug', 'wrapper')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.android-home'
if (Test-Path "$ReadOnlyGradleCache\modules-2") { $env:GRADLE_RO_DEP_CACHE = $ReadOnlyGradleCache }
$extra = @()
if ($Offline) { $extra += '--offline' }
$country = [System.Globalization.RegionInfo]::CurrentRegion.TwoLetterISORegionName
$language = [System.Globalization.CultureInfo]::CurrentCulture.TwoLetterISOLanguageName
$jvmArgs = "-Xmx2048m -Dfile.encoding=UTF-8 -Duser.country=$country -Duser.language=$language -Duser.variant="
& "$JdkHome\bin\java.exe" -Xmx2048m '-Dfile.encoding=UTF-8' "-Duser.country=$country" "-Duser.language=$language" '-Duser.variant=' '-Dorg.gradle.internal.instrumentation.agent=false' -jar "$GradleDistribution\lib\gradle-gradle-cli-main-9.6.0.jar" -g "$projectRoot\.gradle-home" --no-daemon "-Dorg.gradle.jvmargs=$jvmArgs" '-Dorg.gradle.internal.instrumentation.agent=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain @extra @Tasks
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
