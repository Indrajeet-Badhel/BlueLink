param(
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [Parameter(Mandatory=$true)][string]$GradleDistribution,
    [string]$DependencyCache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDir = Join-Path $projectRoot 'protocol\build\manual-tests'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$javaExe = Join-Path $JdkHome 'bin\java.exe'
$stdlib = (Get-ChildItem "$GradleDistribution\lib" -Filter 'kotlin-stdlib-*.jar' | Select-Object -First 1).FullName
$junit = (Get-ChildItem "$DependencyCache\junit\junit\4.13.2" -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1).FullName
$hamcrest = (Get-ChildItem "$DependencyCache\org.hamcrest\hamcrest-core\1.3" -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1).FullName
if (!$stdlib -or !$junit -or !$hamcrest) { throw 'Compiler or JUnit dependencies are not cached. Run the standard Gradle test task after connecting to Maven.' }
$classPath = "$stdlib;$junit;$hamcrest"
$sources = @(Get-ChildItem "$projectRoot\protocol\src" -Recurse -Filter '*.kt' | ForEach-Object FullName)
& $javaExe -cp "$GradleDistribution\lib\*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $classPath -d $outputDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Protocol Kotlin compilation failed' }
& $javaExe -cp "$outputDir;$classPath" org.junit.runner.JUnitCore dev.bluelink.protocol.PacketTest dev.bluelink.protocol.SessionTest dev.bluelink.protocol.FeaturesTest dev.bluelink.protocol.GamesTest
if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed' }
