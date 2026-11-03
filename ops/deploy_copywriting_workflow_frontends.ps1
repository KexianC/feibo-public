[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9]{8}-[0-9]{6}(-[A-Za-z0-9._-]+)?$')]
    [string]$ReleaseId,

    [string]$DdArchive = (Join-Path $PSScriptRoot '..\release\copywriting-workflow-20260812-1615\artifacts\dd-copywriting-workflow-20260812-1615.tar.gz'),
    [string]$ManageArchive = (Join-Path $PSScriptRoot '..\release\copywriting-workflow-20260812-1615\artifacts\manage-copywriting-workflow-20260812-1615.tar.gz'),
    [string]$RemoteDeployer = (Join-Path $PSScriptRoot 'deploy_feibo_frontends_remote.sh'),

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$ExpectedDdSha256,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$ExpectedManageSha256,

    [ValidatePattern('^[A-Za-z0-9.-]+$')]
    [Parameter(Mandatory = $true)]
    [string]$Server,
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_-]*$')]
    [string]$SshUser = 'root',
    [string]$SshKey = (Join-Path $env:USERPROFILE '.ssh\id_ed25519'),
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$ssh = 'C:\Windows\System32\OpenSSH\ssh.exe'
$scp = 'C:\Windows\System32\OpenSSH\scp.exe'
$target = "$SshUser@$Server"
$remoteStage = "/root/codex-feibo-frontend-deploy/$ReleaseId"

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)] [string]$FilePath,
        [Parameter(Mandatory = $true)] [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath failed with exit code $LASTEXITCODE"
    }
}

function Assert-Archive {
    param(
        [Parameter(Mandatory = $true)] [string]$Path,
        [Parameter(Mandatory = $true)] [string]$ExpectedSha256,
        [Parameter(Mandatory = $true)] [string[]]$RequiredEntries
    )

    $resolved = (Resolve-Path -LiteralPath $Path).Path
    if ((Get-Item -LiteralPath $resolved).Length -le 0) {
        throw "Archive is empty: $resolved"
    }

    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolved).Hash.ToLowerInvariant()
    if ($actualHash -ne $ExpectedSha256.ToLowerInvariant()) {
        throw "SHA-256 mismatch for $resolved. Expected $ExpectedSha256, got $actualHash"
    }

    $entries = @(& tar.exe -tzf $resolved)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to read archive: $resolved"
    }
    if ($entries.Count -eq 0) {
        throw "Archive contains no entries: $resolved"
    }
    foreach ($entry in $entries) {
        if ($entry -match '[\x00-\x1f\x7f]' -or $entry -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$)|\\)') {
            throw "Unsafe archive entry '$entry' in $resolved"
        }
    }
    $verboseEntries = @(& tar.exe -tvzf $resolved)
    if ($LASTEXITCODE -ne 0 -or $verboseEntries.Count -ne $entries.Count) {
        throw "Unable to inspect archive member types: $resolved"
    }
    foreach ($verboseEntry in $verboseEntries) {
        if (-not ($verboseEntry.StartsWith('-') -or $verboseEntry.StartsWith('d'))) {
            throw "Archive contains a link or special member: $resolved"
        }
    }
    foreach ($requiredEntry in $RequiredEntries) {
        if ($entries -notcontains $requiredEntry) {
            throw "Required archive entry '$requiredEntry' is missing from $resolved"
        }
    }

    [pscustomobject]@{
        Path = $resolved
        Sha256 = $actualHash
        Size = (Get-Item -LiteralPath $resolved).Length
    }
}

if (-not (Test-Path -LiteralPath $SshKey -PathType Leaf)) {
    throw "SSH key not found: $SshKey"
}
if (-not (Test-Path -LiteralPath $RemoteDeployer -PathType Leaf)) {
    throw "Remote deployer not found: $RemoteDeployer"
}

$dd = Assert-Archive -Path $DdArchive -ExpectedSha256 $ExpectedDdSha256 -RequiredEntries @('./index.html', './assets/')
$manage = Assert-Archive -Path $ManageArchive -ExpectedSha256 $ExpectedManageSha256 -RequiredEntries @('./index.html', './assets/', './geo-agent-app/index.html')
$deployerPath = (Resolve-Path -LiteralPath $RemoteDeployer).Path
$deployerSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $deployerPath).Hash.ToLowerInvariant()

Write-Output 'FRONTEND_CANDIDATE_OK'
Write-Output "RELEASE=$ReleaseId"
Write-Output "DD_SHA256=$($dd.Sha256)"
Write-Output "DD_SIZE=$($dd.Size)"
Write-Output "MANAGE_SHA256=$($manage.Sha256)"
Write-Output "MANAGE_SIZE=$($manage.Size)"
Write-Output "DEPLOYER_SHA256=$deployerSha256"

if (-not $Apply) {
    Write-Output 'MODE=validate-only'
    Write-Output 'No remote files or symlinks were changed. Re-run with -Apply after the backend and database gates pass.'
    exit 0
}

$sshArgs = @('-i', $SshKey, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes')
Invoke-Native $ssh ($sshArgs + @($target, "install -d -m 700 '$remoteStage'"))
Invoke-Native $scp ($sshArgs + @($dd.Path, "${target}:$remoteStage/dd.tar.gz"))
Invoke-Native $scp ($sshArgs + @($manage.Path, "${target}:$remoteStage/manage.tar.gz"))
Invoke-Native $scp ($sshArgs + @($deployerPath, "${target}:$remoteStage/deploy.sh"))

$ddRemote = "$remoteStage/dd.tar.gz"
$manageRemote = "$remoteStage/manage.tar.gz"
$remoteCommand = @(
    "set -Eeuo pipefail"
    "printf '%s  %s\n' '$($dd.Sha256)' '$ddRemote' '$($manage.Sha256)' '$manageRemote' '$deployerSha256' '$remoteStage/deploy.sh' | sha256sum -c -"
    "chmod 700 '$remoteStage/deploy.sh'"
    "printf 'PREVIOUS_DD='; readlink /www/wwwroot/dd.hzmwmt.com/current || true"
    "printf 'PREVIOUS_MANAGE='; readlink /www/wwwroot/manage.hzmwmt.com/current || true"
    "'$remoteStage/deploy.sh' '$ddRemote' '$manageRemote' '$ReleaseId'"
    "printf 'CURRENT_DD='; readlink /www/wwwroot/dd.hzmwmt.com/current"
    "printf 'CURRENT_MANAGE='; readlink /www/wwwroot/manage.hzmwmt.com/current"
) -join '; '

Invoke-Native $ssh ($sshArgs + @($target, $remoteCommand))
Write-Output 'FRONTEND_DEPLOY_COMPLETE'
