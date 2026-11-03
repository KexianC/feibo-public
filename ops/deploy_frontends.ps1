param(
    [Parameter(Mandatory = $true)]
    [string]$Server,
    [string]$SshUser = 'root',
    [string]$SshKey = (Join-Path $env:USERPROFILE '.ssh\id_ed25519'),
    [switch]$SkipInstall,
    [switch]$SkipBuild,
    [switch]$BuildGeo
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ddProject = Join-Path $workspace 'feibo'
$manageProject = Join-Path $workspace 'feibo_backend'
$geoProject = Join-Path $workspace 'geo-agentw-article-f-master\social-publish\fontback'
$releaseRoot = Join-Path $workspace 'release'
$releaseId = Get-Date -Format 'yyyyMMdd-HHmmss'
$localStage = Join-Path $releaseRoot "frontend-deploy-$releaseId"
$ddArchive = Join-Path $localStage "dd-$releaseId.tar.gz"
$manageArchive = Join-Path $localStage "manage-$releaseId.tar.gz"
$remoteStage = "/root/codex-feibo-frontend-deploy/$releaseId"
$ssh = 'C:\Windows\System32\OpenSSH\ssh.exe'
$scp = 'C:\Windows\System32\OpenSSH\scp.exe'
$target = "$SshUser@$Server"

function Invoke-Native {
    param([string]$FilePath, [string[]]$Arguments)
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path -LiteralPath $SshKey)) {
    throw "SSH key not found: $SshKey"
}

if ($BuildGeo) {
    if (-not $SkipInstall) {
        Invoke-Native 'npm.cmd' @('--prefix', $geoProject, 'ci')
    }
    Invoke-Native 'npm.cmd' @('--prefix', $geoProject, 'run', 'build:embedded')
}

if (-not $SkipBuild) {
    foreach ($project in @($ddProject, $manageProject)) {
        if (-not $SkipInstall) {
            Invoke-Native 'npm.cmd' @('--prefix', $project, 'ci')
        }
        Invoke-Native 'npm.cmd' @('--prefix', $project, 'run', 'build')
    }
}

$ddDist = Join-Path $ddProject 'dist'
$manageDist = Join-Path $manageProject 'dist'
if (-not (Test-Path -LiteralPath (Join-Path $ddDist 'index.html'))) {
    throw 'dd build artifact is missing index.html'
}
if (-not (Test-Path -LiteralPath (Join-Path $manageDist 'index.html'))) {
    throw 'manage build artifact is missing index.html'
}
if (-not (Test-Path -LiteralPath (Join-Path $manageDist 'geo-agent-app\index.html'))) {
    throw 'manage build artifact is missing geo-agent-app/index.html'
}

New-Item -ItemType Directory -Force -Path $localStage | Out-Null
Invoke-Native 'tar.exe' @('-czf', $ddArchive, '-C', $ddDist, '.')
Invoke-Native 'tar.exe' @('-czf', $manageArchive, '-C', $manageDist, '.')

$sshArgs = @('-i', $SshKey, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes')
Invoke-Native $ssh ($sshArgs + @($target, "install -d -m 700 '$remoteStage'"))
Invoke-Native $scp ($sshArgs + @($ddArchive, $manageArchive, "${target}:$remoteStage/"))
Invoke-Native $ssh ($sshArgs + @(
    $target,
    "/usr/local/sbin/deploy-feibo-frontends.sh '$remoteStage/$(Split-Path $ddArchive -Leaf)' '$remoteStage/$(Split-Path $manageArchive -Leaf)' '$releaseId'"
))

Get-FileHash -Algorithm SHA256 $ddArchive, $manageArchive |
    Select-Object Path, Hash
