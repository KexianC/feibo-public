[CmdletBinding()]
param(
    [string]$BaselineJar,
    [string]$SourceRoot,
    [string]$ReleaseAllowlist,
    [string]$SourceManifest,
    [string]$OutputDirectory,
    [string]$JavaExecutable = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\java.exe',
    [string]$MavenExecutable = 'C:\Users\hzmwm\.local\tools\apache-maven-3.9.11\bin\mvn.cmd'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$expectedBaselineSha = '65368b3d5746d8284d8154bf70f073acad49b052c5bcc29071a0b1432d31ef68'
$expectedBaselineSize = 213838200L
$expectedAllowlistSha = 'ebf2f36075421030497ef8defc5be8e21ded1e2f562cf49d263e243abe6d9a0f'
$expectedSourceManifestSha = '08bf1e2d44e442fcf19d9f416adbfedda13592c7c60a18431803cd0d3b0cc485'
$candidateName = 'digital-doctor-app.copywriting-audit-detail.candidate.jar'
$targetedTests = @(
    'com.hzmwmt.config.CopyWritingRepositoryAuditTest',
    'com.hzmwmt.config.CopywritingAuditDtoContractTest',
    'com.hzmwmt.config.CopywritingAuditMapperContractTest',
    'com.hzmwmt.config.CopywritingControllerAuditTest',
    'com.hzmwmt.config.CopywritingMapperContractTest'
)

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Assert-RegularFile([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label is missing: $Path"
    }
    if (((Get-Item -LiteralPath $Path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label must not be a symlink/reparse point: $Path"
    }
}

function Assert-Directory([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Label is missing: $Path"
    }
    if (((Get-Item -LiteralPath $Path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label must not be a symlink/reparse point: $Path"
    }
}

function Read-SourceManifest([string]$Path) {
    $result = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if (-not $line) { continue }
        if ($line -notmatch '^([0-9a-f]{64})  (feibo_backend_master/.+)$') {
            throw "Malformed source-manifest line: $line"
        }
        $hash = $Matches[1]
        $name = $Matches[2]
        if ($result.Contains($name)) {
            throw "Duplicate source-manifest path: $name"
        }
        $result[$name] = $hash
    }
    return $result
}

function Read-Allowlist([string]$Path) {
    $result = [Collections.Generic.List[string]]::new()
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in Get-Content -LiteralPath $Path) {
        $name = $line.Trim().Replace('\', '/')
        if (-not $name -or $name.StartsWith('#')) { continue }
        if (-not $name.StartsWith('feibo_backend_master/', [StringComparison]::Ordinal) -or
            $name.Contains('../') -or $name.Contains('/..') -or $name.Contains(':')) {
            throw "Unsafe allowlist path: $name"
        }
        if (-not $seen.Add($name)) {
            throw "Duplicate allowlist path: $name"
        }
        $result.Add($name)
    }
    return $result
}

function Assert-ManifestMatchesTree(
    [Collections.IDictionary]$Manifest,
    [string]$TreeRoot,
    [string]$TreePrefix
) {
    foreach ($item in $Manifest.GetEnumerator()) {
        $relative = $item.Key.Substring($TreePrefix.Length).Replace('/', [IO.Path]::DirectorySeparatorChar)
        $path = Join-Path $TreeRoot $relative
        Assert-RegularFile $path "Pinned runtime source"
        $actual = Get-Sha256 $path
        if ($actual -ne $item.Value) {
            throw "Pinned source hash mismatch: $($item.Key); expected=$($item.Value); actual=$actual"
        }
    }
}

function Write-ReleaseChecksums([string]$Directory) {
    $lines = Get-ChildItem -LiteralPath $Directory -File | Where-Object {
        $_.Name -ne 'SHA256SUMS.txt'
    } | Sort-Object -Property Name | ForEach-Object {
        "$(Get-Sha256 $_.FullName)  $($_.Name)"
    }
    [IO.File]::WriteAllText(
        (Join-Path $Directory 'SHA256SUMS.txt'),
        (($lines -join "`n") + "`n"),
        [Text.UTF8Encoding]::new($false)
    )
}

$opsDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspace = Split-Path -Parent $opsDirectory
if (-not $BaselineJar) {
    $BaselineJar = Join-Path $workspace 'release\copywriting-history-hotfix-20260818-111500\digital-doctor-app.copywriting-history-hotfix.candidate.jar'
}
if (-not $SourceRoot) {
    $SourceRoot = Join-Path $workspace 'feibo_backend_master'
}
if (-not $ReleaseAllowlist) {
    $ReleaseAllowlist = Join-Path $opsDirectory 'copywriting_audit_detail_release_files.txt'
}
if (-not $SourceManifest) {
    $SourceManifest = Join-Path $opsDirectory 'copywriting_audit_detail_source.sha256'
}
if (-not $OutputDirectory) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputDirectory = Join-Path $workspace "release\copywriting-audit-detail-$stamp\backend"
}

$BaselineJar = [IO.Path]::GetFullPath($BaselineJar)
$SourceRoot = [IO.Path]::GetFullPath($SourceRoot)
$ReleaseAllowlist = [IO.Path]::GetFullPath($ReleaseAllowlist)
$SourceManifest = [IO.Path]::GetFullPath($SourceManifest)
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$builderSource = Join-Path $opsDirectory 'CopywritingAuditDetailJarBuilder.java'
$wrapperSource = $MyInvocation.MyCommand.Path
$buildLockPath = Join-Path $workspace 'release\copywriting-audit-detail-backend-build.lock'

Assert-RegularFile $BaselineJar 'Pinned production baseline JAR'
Assert-Directory $SourceRoot 'Backend source root'
Assert-RegularFile $ReleaseAllowlist 'Release allowlist'
Assert-RegularFile $SourceManifest 'Source hash manifest'
Assert-RegularFile $builderSource 'Candidate builder source'
Assert-RegularFile $JavaExecutable 'Pinned Java executable'
Assert-RegularFile $MavenExecutable 'Pinned Maven executable'

if ((Get-Item -LiteralPath $BaselineJar).Length -ne $expectedBaselineSize -or
    (Get-Sha256 $BaselineJar) -ne $expectedBaselineSha) {
    throw 'Baseline JAR is not the exact current production 65368.../213838200 artifact.'
}
if ((Get-Sha256 $ReleaseAllowlist) -ne $expectedAllowlistSha) {
    throw 'Release allowlist checksum mismatch.'
}
if ((Get-Sha256 $SourceManifest) -ne $expectedSourceManifestSha) {
    throw 'Source manifest checksum mismatch.'
}
if (Test-Path -LiteralPath $OutputDirectory) {
    throw "Output directory already exists; refusing overwrite: $OutputDirectory"
}

$allowlist = Read-Allowlist $ReleaseAllowlist
$manifest = Read-SourceManifest $SourceManifest
if ($allowlist.Count -ne 16 -or $manifest.Count -ne 16) {
    throw "Expected exactly 16 allowlisted runtime source/resource files; allowlist=$($allowlist.Count), manifest=$($manifest.Count)"
}
$allowSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($name in $allowlist) { [void]$allowSet.Add($name) }
$manifestSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($name in $manifest.Keys) { [void]$manifestSet.Add([string]$name) }
if (-not $allowSet.SetEquals($manifestSet)) {
    throw 'Source manifest and release allowlist path sets differ.'
}

$sourceParent = Split-Path -Parent $SourceRoot
$sourceLeaf = Split-Path -Leaf $SourceRoot
if ($sourceLeaf -ne 'feibo_backend_master') {
    throw "Source root leaf must be feibo_backend_master: $SourceRoot"
}
$geoSourceRoot = Join-Path $sourceParent 'geo-agentw-article-f-master'
Assert-Directory $geoSourceRoot 'Sibling GEO Maven module root'
Assert-ManifestMatchesTree $manifest $SourceRoot 'feibo_backend_master/'

$buildLock = $null
$tempRoot = $null
try {
    try {
        $buildLock = [IO.File]::Open($buildLockPath, 'OpenOrCreate', 'ReadWrite', 'None')
    } catch {
        throw "Another audit-detail backend candidate build holds the exclusive lock: $buildLockPath"
    }

    $tempBase = Join-Path $workspace 'release\copywriting-audit-detail-build-work'
    [IO.Directory]::CreateDirectory($tempBase) | Out-Null
    $tempRoot = Join-Path $tempBase ("codex-copywriting-audit-detail-" + [Guid]::NewGuid().ToString('N'))
    $isolatedRoot = Join-Path $tempRoot 'feibo_backend_master'
    $isolatedGeoRoot = Join-Path $tempRoot 'geo-agentw-article-f-master'
    $mavenRepository = Join-Path $tempRoot 'm2-repository'
    [IO.Directory]::CreateDirectory($isolatedRoot) | Out-Null
    [IO.Directory]::CreateDirectory($isolatedGeoRoot) | Out-Null
    [IO.Directory]::CreateDirectory($mavenRepository) | Out-Null
    $copyLog = Join-Path $tempRoot 'source-copy.log'
    $geoCopyLog = Join-Path $tempRoot 'geo-source-copy.log'

    & robocopy $SourceRoot $isolatedRoot /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ `
        /XD target bin .git '.git*' .idea /XF '*.7z' '*.class' /NFL /NDL /NP /LOG:$copyLog
    $copyExit = $LASTEXITCODE
    if ($copyExit -ge 8) {
        throw "Isolated source copy failed with robocopy exit code $copyExit"
    }
    & robocopy $geoSourceRoot $isolatedGeoRoot /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ `
        /XD target bin .git '.git*' .idea /XF '*.7z' '*.class' /NFL /NDL /NP /LOG:$geoCopyLog
    $geoCopyExit = $LASTEXITCODE
    if ($geoCopyExit -ge 8) {
        throw "Isolated GEO source copy failed with robocopy exit code $geoCopyExit"
    }
    Assert-ManifestMatchesTree $manifest $isolatedRoot 'feibo_backend_master/'

    # Seed a private per-build Maven repository from the existing cache to
    # avoid network variance. The coordinates written by an earlier aborted
    # build are excluded and rebuilt from this isolated source tree below.
    $globalMavenRepository = 'C:\Users\hzmwm\.m2\repository'
    Assert-Directory $globalMavenRepository 'Read-only Maven cache seed'
    $mavenSeedLog = Join-Path $tempRoot 'maven-repository-seed.log'
    $excludedParentCoordinate = Join-Path $globalMavenRepository 'com\hzmwmt\digital-doctor'
    $excludedGeoCoordinate = Join-Path $globalMavenRepository 'com\hzmwmt\ai\geo-agent'
    & robocopy $globalMavenRepository $mavenRepository /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ `
        /XD $excludedParentCoordinate $excludedGeoCoordinate /NFL /NDL /NP /LOG:$mavenSeedLog
    $mavenSeedExit = $LASTEXITCODE
    if ($mavenSeedExit -ge 8) {
        throw "Private Maven repository seed failed with robocopy exit code $mavenSeedExit"
    }

    $parentMavenLog = Join-Path $tempRoot 'maven-parent-prerequisite.log'
    & $MavenExecutable -f (Join-Path $isolatedRoot 'pom.xml') -N `
        install '-Dmaven.test.skip=true' "-Dmaven.repo.local=$mavenRepository" `
        2>&1 | Tee-Object -FilePath $parentMavenLog
    $parentMavenExit = $LASTEXITCODE
    if ($parentMavenExit -ne 0) {
        throw "Isolated parent-POM prerequisite install failed with exit code $parentMavenExit"
    }

    $geoMavenLog = Join-Path $tempRoot 'maven-geo-prerequisite.log'
    & $MavenExecutable -f (Join-Path $isolatedRoot 'pom.xml') -pl ':geo-agent' `
        clean install '-Dmaven.test.skip=true' "-Dmaven.repo.local=$mavenRepository" `
        2>&1 | Tee-Object -FilePath $geoMavenLog
    $geoMavenExit = $LASTEXITCODE
    if ($geoMavenExit -ne 0) {
        throw "Isolated GEO prerequisite build failed with exit code $geoMavenExit"
    }

    $mavenLog = Join-Path $tempRoot 'maven-clean-package.log'
    $testSelector = ($targetedTests | ForEach-Object { ($_ -split '\.')[-1] }) -join ','
    $releaseModules = 'digital-doctor-types,digital-doctor-domain,digital-doctor-infrastructure,digital-doctor-api,digital-doctor-geo,digital-doctor-trigger,digital-doctor-app'
    & $MavenExecutable -f (Join-Path $isolatedRoot 'pom.xml') -pl $releaseModules `
        clean package '-DskipTests=false' "-Dtest=$testSelector" '-Dsurefire.failIfNoSpecifiedTests=false' `
        "-Dmaven.repo.local=$mavenRepository" `
        2>&1 | Tee-Object -FilePath $mavenLog
    $mavenExit = $LASTEXITCODE
    if ($mavenExit -ne 0) {
        throw "Isolated Maven clean package/test failed with exit code $mavenExit"
    }

    $testSummary = [Collections.Generic.List[string]]::new()
    $testSummary.Add('targeted_test_gate=passed')
    $testTotal = 0
    foreach ($testClass in $targetedTests) {
        $report = Join-Path $isolatedRoot ("digital-doctor-app\target\surefire-reports\TEST-$testClass.xml")
        Assert-RegularFile $report "Targeted Surefire report for $testClass"
        [xml]$xml = Get-Content -Raw -LiteralPath $report
        $suite = $xml.testsuite
        $tests = [int]$suite.tests
        $errors = [int]$suite.errors
        $failures = [int]$suite.failures
        $skipped = [int]$suite.skipped
        if ($tests -lt 1 -or $errors -ne 0 -or $failures -ne 0) {
            throw "Targeted test report did not pass: $testClass tests=$tests errors=$errors failures=$failures"
        }
        $testTotal += $tests
        $testSummary.Add("$testClass tests=$tests errors=$errors failures=$failures skipped=$skipped")
    }
    $testSummary.Add("targeted_test_classes=$($targetedTests.Count)")
    $testSummary.Add("targeted_test_methods=$testTotal")

    foreach ($name in $allowlist) {
        $moduleRelative = $name.Substring('feibo_backend_master/'.Length)
        $module = $moduleRelative.Substring(0, $moduleRelative.IndexOf('/'))
        $sourceRelative = $moduleRelative.Substring($module.Length + 1)
        $sourcePath = Join-Path (Join-Path $isolatedRoot $module) $sourceRelative.Replace('/', '\')
        if ($name.EndsWith('.java', [StringComparison]::Ordinal)) {
            $classRelative = $sourceRelative.Substring('src/main/java/'.Length)
            $classRelative = $classRelative.Substring(0, $classRelative.Length - 5) + '.class'
            $compiled = Join-Path (Join-Path $isolatedRoot "$module\target\classes") $classRelative.Replace('/', '\')
        } else {
            $resourceRelative = $sourceRelative.Substring('src/main/resources/'.Length)
            $compiled = Join-Path (Join-Path $isolatedRoot "$module\target\classes") $resourceRelative.Replace('/', '\')
        }
        Assert-RegularFile $compiled 'Fresh compiled allowlisted payload'
        if ((Get-Item -LiteralPath $compiled).LastWriteTimeUtc -lt (Get-Item -LiteralPath $sourcePath).LastWriteTimeUtc) {
            throw "Compiled payload predates its pinned source: $compiled"
        }
    }

    $outputParent = Split-Path -Parent $OutputDirectory
    [IO.Directory]::CreateDirectory($outputParent) | Out-Null
    Assert-Directory $outputParent 'Candidate output parent'
    & $JavaExecutable $builderSource $BaselineJar $isolatedRoot $ReleaseAllowlist $OutputDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "Candidate builder failed with exit code $LASTEXITCODE"
    }

    $candidate = Join-Path $OutputDirectory $candidateName
    Assert-RegularFile $candidate 'Built candidate JAR'
    Copy-Item -LiteralPath $SourceManifest -Destination (Join-Path $OutputDirectory 'source-files.sha256')
    Copy-Item -LiteralPath $builderSource -Destination (Join-Path $OutputDirectory 'CopywritingAuditDetailJarBuilder.java')
    Copy-Item -LiteralPath $wrapperSource -Destination (Join-Path $OutputDirectory 'build_copywriting_audit_detail_backend_candidate.ps1')
    Copy-Item -LiteralPath $copyLog -Destination (Join-Path $OutputDirectory 'source-copy.log')
    Copy-Item -LiteralPath $geoCopyLog -Destination (Join-Path $OutputDirectory 'geo-source-copy.log')
    Copy-Item -LiteralPath $mavenSeedLog -Destination (Join-Path $OutputDirectory 'maven-repository-seed.log')
    Copy-Item -LiteralPath $parentMavenLog -Destination (Join-Path $OutputDirectory 'maven-parent-prerequisite.log')
    Copy-Item -LiteralPath $geoMavenLog -Destination (Join-Path $OutputDirectory 'maven-geo-prerequisite.log')
    Copy-Item -LiteralPath $mavenLog -Destination (Join-Path $OutputDirectory 'maven-clean-package.log')
    [IO.File]::WriteAllText(
        (Join-Path $OutputDirectory 'targeted-tests.txt'),
        (($testSummary -join "`n") + "`n"),
        [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText(
        (Join-Path $OutputDirectory 'build-wrapper-audit.txt'),
        ((@(
            'release=copywriting-audit-detail-history',
            "baseline_sha256=$expectedBaselineSha",
            "baseline_size=$expectedBaselineSize",
            "allowlist_sha256=$expectedAllowlistSha",
            "source_manifest_sha256=$expectedSourceManifestSha",
            "candidate_sha256=$(Get-Sha256 $candidate)",
            "candidate_size=$((Get-Item -LiteralPath $candidate).Length)",
            'isolated_source_copy=yes',
            'maven_repository=private_per_build',
            'maven_repository_seed_excluded=com.hzmwmt:digital-doctor,com.hzmwmt.ai:geo-agent',
            'geo_prerequisite_main_compile=passed',
            'geo_prerequisite_tests=skipped_existing_unrelated_test_compile_failure',
            'maven_clean_package=yes',
            'targeted_tests=passed',
            'database_change=none',
            'production_touched=no'
        ) -join "`n") + "`n"),
        [Text.UTF8Encoding]::new($false)
    )
    Write-ReleaseChecksums $OutputDirectory

    Write-Output 'COPYWRITING_AUDIT_DETAIL_BUILD_OK'
    Write-Output "OUTPUT_DIRECTORY=$OutputDirectory"
    Write-Output "CANDIDATE=$candidate"
    Write-Output "CANDIDATE_SHA256=$(Get-Sha256 $candidate)"
    Write-Output "CANDIDATE_SIZE=$((Get-Item -LiteralPath $candidate).Length)"
    Write-Output "SOURCE_MANIFEST_SHA256=$expectedSourceManifestSha"
    Write-Output "TARGETED_TEST_METHODS=$testTotal"
} finally {
    if ($null -ne $buildLock) {
        $buildLock.Dispose()
    }
    if ($null -ne $tempRoot -and (Test-Path -LiteralPath $tempRoot)) {
        # Keep the exact isolated build tree for audit. Cleanup is deliberately
        # not automatic because release tooling must never recursively delete a
        # computed path after a failed build.
        Write-Output "ISOLATED_BUILD_TREE_RETAINED=$tempRoot"
    }
}
