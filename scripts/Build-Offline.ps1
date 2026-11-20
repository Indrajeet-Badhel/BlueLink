param(
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$GradleDistribution,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [Parameter(Mandatory=$true)][string]$KotlinCompilerHome,
    [Parameter(Mandatory=$true)][string]$Python
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
$aaptOverride = "-Pandroid.aapt2FromMavenOverride=$AndroidSdk\build-tools\36.1.0\aapt2.exe"
$parameters = @{JdkHome=$JdkHome;GradleDistribution=$GradleDistribution;AndroidSdk=$AndroidSdk;Offline=$true}
& "$PSScriptRoot\Build-Local.ps1" @parameters -Tasks @('-I','scripts/export-artifacts.init.gradle',$aaptOverride,':app:exportRuntimeArtifacts',':app:processDebugResources',':app:generateDebugBuildConfig','-x',':protocol:compileKotlin')
& $Python "$PSScriptRoot\compile_offline.py" --jdk $JdkHome --kotlinc $KotlinCompilerHome --sdk $AndroidSdk
if ($LASTEXITCODE -ne 0) { throw 'Kotlin source compilation failed' }
$javaOutput = Join-Path $root 'app\build\intermediates\javac\debug\compileDebugJavaWithJavac\classes'
New-Item -ItemType Directory -Path $javaOutput -Force | Out-Null
& "$JdkHome\bin\javac.exe" --release 17 -d $javaOutput 'app\build\generated\source\buildConfig\debug\dev\bluelink\BuildConfig.java'
if ($LASTEXITCODE -ne 0) { throw 'BuildConfig compilation failed' }
& "$PSScriptRoot\Build-Local.ps1" @parameters -Tasks @($aaptOverride,':app:assembleDebug','-x',':protocol:compileKotlin','-x',':app:compileDebugKotlin','-x',':app:compileDebugJavaWithJavac')
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
& "$JdkHome\bin\java.exe" -jar "$AndroidSdk\build-tools\36.1.0\lib\apksigner.jar" verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
New-Item -ItemType Directory -Path (Join-Path $root 'dist') -Force | Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $root 'dist\BlueLink_2.0.apk')
Write-Output 'Built and signature-verified dist\BlueLink_2.0.apk'
