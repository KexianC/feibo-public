[CmdletBinding()]
param(
    [string]$BaselineJar,
    [string]$ClassesRoot,
    [string]$ReleaseAllowlist,
    [string]$OutputDirectory,
    [string]$JavaExecutable = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\java.exe',
    [string]$MavenExecutable = 'C:\Users\hzmwm\.local\tools\apache-maven-3.9.11\bin\mvn.cmd'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$opsDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspace = Split-Path -Parent $opsDirectory
$buildLockPath = Join-Path $workspace 'release\copywriting-workflow-backend-build.lock'
$buildLock = $null
try {
    $buildLock = [System.IO.File]::Open($buildLockPath, 'OpenOrCreate', 'ReadWrite', 'None')
} catch {
    throw "Another copywriting-workflow backend candidate build holds the exclusive build lock: $buildLockPath"
}
try {

if (-not $BaselineJar) {
    $BaselineJar = Join-Path $workspace 'release\copywriting-workflow-20260812-1615\artifacts\digital-doctor-app.production-baseline.jar'
}
if (-not $ClassesRoot) {
    $ClassesRoot = Join-Path $workspace 'release\cw-src\feibo_backend_master'
}
if (-not $ReleaseAllowlist) {
    $ReleaseAllowlist = Join-Path $opsDirectory 'copywriting_workflow_release_files.txt'
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $workspace 'release\copywriting-workflow-20260812-1615\artifacts\backend-candidate'
}

$builderSource = Join-Path $opsDirectory 'CopywritingWorkflowJarBuilder.java'
$required = @($BaselineJar, $ClassesRoot, $ReleaseAllowlist, $builderSource, $JavaExecutable, $MavenExecutable)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required path does not exist: $path"
    }
}

$expectedBaselineSha = '9f98d5bb0dbbf5061a1f731bdee5eaacb135ec2081a90816c7e993b9dbb13503'
$expectedAllowlistSha = '710dd6e578e0593b6f6977ca8fb3556bbd0176c1f7706cf86ec97e08155a8f79'
$actualBaselineSha = (Get-FileHash -LiteralPath $BaselineJar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualBaselineSha -ne $expectedBaselineSha) {
    throw "Baseline SHA-256 mismatch. Expected the complete pinned production digest."
}
$actualAllowlistSha = (Get-FileHash -LiteralPath $ReleaseAllowlist -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualAllowlistSha -ne $expectedAllowlistSha) {
    throw "Release allowlist SHA-256 mismatch. Only the pinned reviewed file list is accepted."
}

# Build and test inside the isolated release tree on every candidate build. A
# pre-existing target/classes directory is never trusted as release input.
$mavenRoot = $ClassesRoot
& $MavenExecutable -f (Join-Path $mavenRoot 'pom.xml') -pl digital-doctor-app -am clean package '-DskipTests=false' '-Dtest=CopywritingAiApprovalJobTest' '-Dsurefire.failIfNoSpecifiedTests=false'
if ($LASTEXITCODE -ne 0) {
    throw "Pinned isolated Maven clean package/test failed with exit code $LASTEXITCODE"
}

# The isolated tree must have a successful, fresh targeted test report. Every
# allowlisted Java/XML source must be older than its exact compiled output, so a
# stale target/classes tree cannot be silently consumed.
$testReport = Join-Path $ClassesRoot 'digital-doctor-app\target\surefire-reports\TEST-com.hzmwmt.trigger.job.CopywritingAiApprovalJobTest.xml'
if (-not (Test-Path -LiteralPath $testReport -PathType Leaf)) {
    throw "Missing targeted AI approval test report: $testReport"
}
$testText = Get-Content -Raw -LiteralPath $testReport
if ($testText -notmatch '<testsuite[^>]+tests="3"' -or
    $testText -notmatch '<testsuite[^>]+errors="0"' -or
    $testText -notmatch '<testsuite[^>]+failures="0"') {
    throw "Targeted AI approval test report does not prove 3/3 passing tests."
}

$releasePaths = Get-Content -LiteralPath $ReleaseAllowlist | Where-Object {
    $_ -and -not $_.Trim().StartsWith('#') -and
    ($_.Trim() -like 'feibo_backend_master/*/src/main/java/*.java' -or
     $_.Trim() -like 'feibo_backend_master/*/src/main/resources/*.xml')
}
foreach ($releasePathRaw in $releasePaths) {
    $releasePath = $releasePathRaw.Trim().Replace('/', '\')
    $relative = $releasePath.Substring('feibo_backend_master\'.Length)
    $module = $relative.Substring(0, $relative.IndexOf('\'))
    $moduleRelative = $relative.Substring($module.Length + 1)
    $source = Join-Path (Join-Path $ClassesRoot $module) $moduleRelative
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Pinned runtime source is missing from the isolated tree: $releasePathRaw"
    }
    if ($releasePath.EndsWith('.java')) {
        $compiledRelative = $relative.Substring(($module + '\src\main\java\').Length)
        $compiledRelative = $compiledRelative.Substring(0, $compiledRelative.Length - '.java'.Length) + '.class'
    } else {
        $compiledRelative = $relative.Substring(($module + '\src\main\resources\').Length)
    }
    $compiled = Join-Path (Join-Path $ClassesRoot ($module + '\target\classes')) $compiledRelative
    if (-not (Test-Path -LiteralPath $compiled -PathType Leaf)) {
        throw "Pinned compiled runtime output is missing: $compiled"
    }
    if ((Get-Item -LiteralPath $compiled).LastWriteTimeUtc -lt (Get-Item -LiteralPath $source).LastWriteTimeUtc) {
        throw "Stale compiled runtime output is older than its source: $compiled"
    }
}
if ((Get-Item -LiteralPath $testReport).LastWriteTimeUtc -lt (Get-Item -LiteralPath (Join-Path $ClassesRoot 'digital-doctor-trigger\src\main\java\com\hzmwmt\trigger\job\CopywritingAiApprovalJob.java')).LastWriteTimeUtc) {
    throw "Targeted AI approval test report predates the current source snapshot."
}

if (Test-Path -LiteralPath $OutputDirectory) {
    throw "Output directory must not already exist; refusing any overwrite or symlink traversal: $OutputDirectory"
}

& $JavaExecutable $builderSource $BaselineJar $ClassesRoot $ReleaseAllowlist $OutputDirectory
if ($LASTEXITCODE -ne 0) {
    throw "Backend candidate builder failed with exit code $LASTEXITCODE"
}

$candidate = Join-Path $OutputDirectory 'digital-doctor-app.copywriting-workflow.candidate.jar'
if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
    throw "Builder reported success but candidate JAR is missing: $candidate"
}

Write-Output "Candidate and audit artifacts are ready in: $OutputDirectory"
} finally {
    if ($null -ne $buildLock) {
        $buildLock.Dispose()
    }
}
