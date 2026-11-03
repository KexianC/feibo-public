[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Assert-Matches {
    param(
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][string]$Message
    )
    if ($Content -notmatch $Pattern) {
        throw "Contract failure: $Message"
    }
}

function Assert-DoesNotMatch {
    param(
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][string]$Message
    )
    if ($Content -match $Pattern) {
        throw "Contract failure: $Message"
    }
}

function Assert-OccursBefore {
    param(
        [Parameter(Mandatory = $true)][string]$Content,
        [Parameter(Mandatory = $true)][string]$FirstPattern,
        [Parameter(Mandatory = $true)][string]$SecondPattern,
        [Parameter(Mandatory = $true)][string]$Message
    )
    $options = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    $first = [regex]::Match($Content, $FirstPattern, $options)
    $second = [regex]::Match($Content, $SecondPattern, $options)
    if (-not $first.Success -or -not $second.Success -or $first.Index -ge $second.Index) {
        throw "Contract failure: $Message"
    }
}

$preflightPath = Join-Path $PSScriptRoot 'rbac_user_role_map_constraints_00_preflight.sql'
$cleanupPath = Join-Path $PSScriptRoot 'rbac_user_role_map_constraints_10_quarantine_cleanup.sql'
$hardenPath = Join-Path $PSScriptRoot 'rbac_user_role_map_constraints_15_harden_not_null.sql'
$applyPath = Join-Path $PSScriptRoot 'rbac_user_role_map_constraints_20_apply.sql'
$rollbackPath = Join-Path $PSScriptRoot 'rbac_user_role_map_constraints_90_rollback.sql'
$runbookPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'RBAC_USER_ROLE_MAP_CONSTRAINTS.md'

$requiredPaths = @(
    $preflightPath,
    $cleanupPath,
    $hardenPath,
    $applyPath,
    $rollbackPath,
    $runbookPath
)
foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Contract failure: required migration asset is missing: $path"
    }
}

$preflight = Get-Content -LiteralPath $preflightPath -Raw -Encoding UTF8
$cleanup = Get-Content -LiteralPath $cleanupPath -Raw -Encoding UTF8
$harden = Get-Content -LiteralPath $hardenPath -Raw -Encoding UTF8
$apply = Get-Content -LiteralPath $applyPath -Raw -Encoding UTF8
$rollback = Get-Content -LiteralPath $rollbackPath -Raw -Encoding UTF8
$runbook = Get-Content -LiteralPath $runbookPath -Raw -Encoding UTF8
$allSql = @($preflight, $cleanup, $harden, $apply, $rollback) -join "`n"

# Read-only preflight: evidence is emitted, and readiness requires clean data
# plus physically NOT NULL mapping keys.
Assert-Matches $preflight '(?is)START\s+TRANSACTION\s+READ\s+ONLY' 'preflight must use a read-only transaction'
Assert-Matches $preflight '(?is)CREATE\s+TEMPORARY\s+TABLE\s+`_rbac_preflight_data_gate`' 'preflight must use a session-only failure gate'
Assert-Matches $preflight '(?is)ready_for_constraint_migration`\s+TINYINT\s+NOT\s+NULL' 'preflight gate must reject blocker state'
Assert-Matches $preflight '(?is)information_schema\.columns.*is_nullable' 'preflight must inspect physical nullability'
Assert-Matches $preflight '(?is)@rbac_preflight_map_user_nullable\s*=\s*''NO''.*@rbac_preflight_map_role_nullable\s*=\s*''NO''' 'preflight readiness must require both mapping keys NOT NULL'
Assert-Matches $preflight '(?is)WHERE\s+user_id\s+IS\s+NULL\s+OR\s+role_id\s+IS\s+NULL' 'preflight must count NULL mapping keys'
Assert-Matches $preflight '(?is)HAVING\s+COUNT\(\*\)\s*>\s*1' 'preflight must detect duplicate mappings'
Assert-Matches $preflight '(?is)source_row_sha256' 'preflight must emit an exact row digest'
Assert-Matches $preflight '(?is)orphan_distinct_rows' 'preflight must expose the distinct orphan count'
Assert-DoesNotMatch $preflight '(?is)\b(?:INSERT|UPDATE|DELETE|REPLACE)\s+(?:INTO\s+|FROM\s+)?`?t_(?:user|role|user_role_map)`?' 'preflight must not mutate RBAC application tables'
Assert-DoesNotMatch $preflight '(?is)CREATE\s+(?!TEMPORARY\s+)TABLE' 'preflight must not create persistent tables'

# Every mutating phase binds the selected schema before persistent helper DDL.
foreach ($phase in @($cleanup, $harden, $apply, $rollback)) {
    Assert-Matches $phase '(?is)@rbac_expected_schema' 'each mutating phase must require the approved schema'
    Assert-Matches $phase '(?is)CREATE\s+TEMPORARY\s+TABLE\s+`_rbac_schema_write_gate`' 'each mutating phase must gate persistent DDL by schema'
    Assert-Matches $phase '(?is)VERSION\(\)\s+NOT\s+LIKE\s+''%MariaDB%''' 'each mutating phase gate must reject MariaDB'
    Assert-Matches $phase '(?is)information_schema\.triggers' 'each mutating phase must reject unreviewed triggers'
}
Assert-OccursBefore $cleanup 'CREATE\s+TEMPORARY\s+TABLE\s+`_rbac_schema_write_gate`' 'CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`t_rbac_user_role_constraint_migration`' 'cleanup schema gate must precede persistent table DDL'
Assert-OccursBefore $harden 'CREATE\s+TEMPORARY\s+TABLE\s+`_rbac_schema_write_gate`' 'DROP\s+PROCEDURE' 'hardening schema gate must precede helper-procedure DDL'
Assert-OccursBefore $apply 'CREATE\s+TEMPORARY\s+TABLE\s+`_rbac_schema_write_gate`' 'DROP\s+PROCEDURE' 'apply schema gate must precede helper-procedure DDL'
Assert-OccursBefore $rollback 'CREATE\s+TEMPORARY\s+TABLE\s+`_rbac_schema_write_gate`' 'DROP\s+PROCEDURE' 'rollback schema gate must precede helper-procedure DDL'

# Cleanup is exact-manifest based, not count-only. It records an aggregate
# digest and deletes only hash-verified rows copied into quarantine.
foreach ($variableName in @(
    'rbac_migration_batch_id',
    'rbac_expected_schema',
    'rbac_change_ticket',
    'rbac_expected_duplicate_groups',
    'rbac_expected_orphan_user_rows',
    'rbac_expected_orphan_role_rows',
    'rbac_expected_orphan_distinct_rows'
)) {
    Assert-Matches $cleanup ([regex]::Escape("@$variableName") + '\s+IS\s+NULL') "cleanup must require @$variableName"
}
Assert-Matches $cleanup '(?is)`_rbac_approved_orphan_manifest`.*source_id.*source_row_sha256' 'cleanup must consume the approved ID/hash manifest'
Assert-Matches $cleanup '(?is)CREATE\s+TEMPORARY\s+TABLE\s+`_rbac_normalized_orphan_manifest`.*BIGINT\s+NOT\s+NULL.*ascii_bin' 'cleanup must normalize the operator manifest to an exact internal schema'
Assert-Matches $cleanup '(?is)approved_manifest_sha256' 'cleanup must persist the aggregate approved manifest digest'
Assert-Matches $cleanup '(?is)v_manifest_matching_rows\s*<>\s*v_expected_orphan_distinct_rows' 'cleanup must bind every live row to the manifest'
Assert-Matches $cleanup '(?is)CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`t_user_role_map_quarantine`' 'cleanup must create the quarantine ledger'
Assert-Matches $cleanup '(?is)v_total_ledger_columns\s*<>\s*26.*v_total_quarantine_columns\s*<>\s*14.*v_ledger_column_mismatches\s*<>\s*0.*v_quarantine_column_mismatches\s*<>\s*0' 'cleanup must reject existing ledger/quarantine schema drift'
Assert-Matches $cleanup '(?is)information_schema\.check_constraints.*v_ledger_status_check_count.*v_quarantine_reason_check_count' 'cleanup must verify enforced audit CHECK contracts'
Assert-Matches $cleanup '(?is)DELETE\s+role_map\s+FROM\s+t_user_role_map\s+AS\s+role_map\s+INNER\s+JOIN\s+t_user_role_map_quarantine' 'cleanup must delete only rows captured in quarantine'
Assert-Matches $cleanup '(?is)v_deleted_rows\s*<>\s*v_quarantined_rows' 'cleanup must compare quarantine and deletion counts'
Assert-Matches $cleanup '(?is)ROLLBACK\s*;\s*.*RESIGNAL' 'cleanup errors must roll back and propagate'
Assert-DoesNotMatch $cleanup '(?is)DELETE\s+FROM\s+`?t_user_role_map`?\s+WHERE' 'cleanup must not use a predicate-only delete'
Assert-DoesNotMatch $cleanup '(?is)INSERT\s+IGNORE' 'cleanup must not hide collisions'
Assert-DoesNotMatch $cleanup '(?im)^\s*SET\s+@rbac_expected_' 'cleanup must not hard-code approved counts'

# NULL hardening is an explicit, resumable, live-type-preserving phase.
Assert-Matches $harden '(?is)migration_status\s*=\s*''NOT_NULL_HARDENING''' 'hardening must persist a pending state before implicit-commit DDL'
Assert-Matches $harden '(?is)migration_status\s*=\s*''NOT_NULL_HARDENED''' 'hardening must persist completion'
Assert-Matches $harden '(?is)LOWER\(column_type\).*information_schema\.columns' 'hardening must read the live column type'
Assert-Matches $harden '(?is)v_user_character_set\s+IS\s+NOT\s+NULL\s+OR\s+v_role_character_set\s+IS\s+NOT\s+NULL' 'hardening must reject character keys that need a fuller definition ledger'
Assert-Matches $harden '(?is)WHERE\s+user_id\s+IS\s+NULL\s+OR\s+role_id\s+IS\s+NULL' 'hardening must reject NULL mapping rows before ALTER'
Assert-Matches $harden '(?is)MODIFY\s+COLUMN\s+`user_id`.*v_original_user_type.*NOT\s+NULL' 'hardening must generate user_id DDL from the recorded live type'
Assert-Matches $harden '(?is)MODIFY\s+COLUMN\s+`role_id`.*v_original_role_type.*NOT\s+NULL' 'hardening must generate role_id DDL from the recorded live type'
Assert-Matches $harden '(?is)v_current_manifest_sha256.*v_audit_manifest_sha256' 'hardening must revalidate the aggregate quarantine manifest'
Assert-OccursBefore $harden "migration_status\s*=\s*'NOT_NULL_HARDENING'" 'PREPARE\s+rbac_not_null_statement' 'hardening pending state must be committed before DDL'
Assert-DoesNotMatch $harden '(?is)MODIFY\s+COLUMN\s+`?(?:user_id|role_id)`?\s+BIGINT' 'hardening must not hard-code a production key type'

# Apply requires the hardened state, commits an ownership plan before DDL,
# verifies exact canonical definitions, and binds FKs to the current schema.
Assert-Matches $apply '(?is)NOT_NULL_HARDENED' 'apply must require the explicit NOT NULL phase'
Assert-Matches $apply '(?is)migration_status\s*=\s*''CONSTRAINTS_APPLYING''' 'apply must persist a pending DDL state'
Assert-OccursBefore $apply "migration_status\s*=\s*'CONSTRAINTS_APPLYING'" 'PREPARE\s+rbac_constraint_statement' 'apply pending state must be committed before DDL'
Assert-Matches $apply '(?is)ADD\s+CONSTRAINT\s+`uk_t_user_role_map_user_role`\s+UNIQUE\s*\(\s*`user_id`\s*,\s*`role_id`\s*\)' 'apply must add the user-role uniqueness invariant'
Assert-Matches $apply '(?is)ADD\s+CONSTRAINT\s+`fk_t_user_role_map_user`\s+FOREIGN\s+KEY\s*\(\s*`user_id`\s*\)\s+REFERENCES\s+`t_user`\s*\(\s*`user_id`\s*\)' 'apply must add the user FK'
Assert-Matches $apply '(?is)ADD\s+CONSTRAINT\s+`fk_t_user_role_map_role`\s+FOREIGN\s+KEY\s*\(\s*`role_id`\s*\)\s+REFERENCES\s+`t_role`\s*\(\s*`role_id`\s*\)' 'apply must add the role FK'
Assert-Matches $apply '(?is)referenced_table_schema\s*=\s*DATABASE\(\)' 'FK equivalence must not accept another schema'
Assert-Matches $apply '(?is)MIN\(index_type\)\s*=\s*''BTREE''.*MIN\(is_visible\)\s*=\s*''YES''.*sub_part\s+IS\s+NOT\s+NULL.*collation\s*<>\s*''A''' 'canonical index fingerprints must include type, visibility, prefixes, and order'
Assert-Matches $apply '(?is)ON\s+UPDATE\s+RESTRICT\s+ON\s+DELETE\s+RESTRICT' 'foreign keys must reject implicit cascades'
Assert-Matches $apply '(?is)v_current_manifest_sha256.*v_audit_manifest_sha256' 'apply must revalidate the aggregate quarantine manifest'
Assert-Matches $apply '(?is)migration_status\s*=\s*''CONSTRAINTS_APPLIED''' 'apply must persist its audit result'
Assert-Matches $apply '(?is)live\s+integrity\s+check\s+failed' 'apply must recheck data immediately before DDL'
Assert-DoesNotMatch $apply '(?is)MODIFY\s+COLUMN' 'apply must not silently alter key nullability or type'

# Rollback validates exact owned definitions, writes resumable boundary states,
# keeps the unique index through restore, and only then finalizes indexes.
Assert-Matches $rollback '(?is)IF\s+v_created_user_fk\s*=\s*1' 'rollback must only drop a user FK owned by the batch'
Assert-Matches $rollback '(?is)IF\s+v_created_role_fk\s*=\s*1' 'rollback must only drop a role FK owned by the batch'
Assert-Matches $rollback '(?is)referenced_table_schema\s*=\s*DATABASE\(\)' 'rollback must fingerprint same-schema FKs'
Assert-Matches $rollback '(?is)MIN\(index_type\)\s*=\s*''BTREE''.*MIN\(is_visible\)\s*=\s*''YES''.*collation\s*<>\s*''A''' 'rollback must fingerprint exact canonical indexes before dropping'
Assert-Matches $rollback '(?is)owned canonical name drift' 'rollback must stop if an owned canonical name is replaced at a retry boundary'
Assert-Matches $rollback '(?is)ROLLBACK_DROPPING_CONSTRAINTS' 'rollback must persist the pre-DDL pending state'
Assert-Matches $rollback '(?is)ROLLBACK_CONSTRAINTS_DROPPED' 'rollback must expose a resumable post-DDL state'
Assert-Matches $rollback '(?is)ROLLBACK_FINALIZING_INDEXES' 'rollback must expose a resumable post-restore state'
Assert-Matches $rollback '(?is)v_audit_status\s+IN\s*\(\s*''QUARANTINED''\s*,\s*''NOT_NULL_HARDENING''\s*,\s*''NOT_NULL_HARDENED''' 'rollback must support aborting before final constraint apply'
Assert-Matches $rollback '(?is)v_audit_status\s*=\s*''CONSTRAINTS_APPLYING''.*partial apply-pending DDL state' 'rollback must reconcile an interrupted apply before restoring data'
Assert-Matches $rollback '(?is)@rbac_confirm_writes_blocked\s+IS\s+NULL.*@rbac_confirm_writes_blocked\s+AS\s+CHAR\)\s*<>\s*''1''' 'rollback must fail closed without an explicit database write-barrier confirmation'
Assert-Matches $rollback '(?is)INSERT\s+INTO\s+t_user_role_map\s*\(\s*id\s*,\s*user_id\s*,\s*role_id\s*,\s*create_time\s*\)' 'rollback must restore original source values'
Assert-Matches $rollback '(?is)v_current_manifest_sha256.*v_audit_manifest_sha256' 'rollback must revalidate the aggregate quarantine manifest'
Assert-Matches $rollback '(?is)restored_at\s*=\s*NOW\(6\)' 'rollback must retain restoration evidence'
Assert-OccursBefore $rollback 'INSERT\s+INTO\s+t_user_role_map' "migration_status\s*=\s*'ROLLBACK_FINALIZING_INDEXES'" 'restoration must finish before final index-drop state'
Assert-OccursBefore $rollback "migration_status\s*=\s*'ROLLBACK_FINALIZING_INDEXES'" 'DROP\s+INDEX\s+`uk_t_user_role_map_user_role`' 'owned uniqueness must remain until after restoration commits'

Assert-DoesNotMatch $allSql '(?is)ON\s+DELETE\s+CASCADE' 'RBAC constraints must never cascade permission deletion'
Assert-DoesNotMatch $allSql '(?is)ON\s+UPDATE\s+CASCADE' 'RBAC constraints must never cascade permission-key changes'
Assert-DoesNotMatch $allSql '(?i)(jdbc:mysql://|password\s*=|101\.37\.255\.45)' 'migration assets must not embed production connection details'

Assert-Matches $runbook '(?is)Flyway/Liquibase' 'runbook must explain the manual migration system'
Assert-Matches $runbook '(?is)mysql --force' 'runbook must preserve fail-closed client behavior'
Assert-Matches $runbook '(?is)_rbac_approved_orphan_manifest' 'runbook must document exact ID/hash approval'
Assert-Matches $runbook '(?is)rbac_user_role_map_constraints_15_harden_not_null\.sql' 'runbook must document explicit NOT NULL hardening'
Assert-Matches $runbook '(?is)CONSTRAINTS_APPLYING' 'runbook must document implicit-commit reconciliation state'
Assert-Matches $runbook '(?is)rbac_user_role_map_constraints_90_rollback\.sql' 'runbook must document recovery'
Assert-Matches $runbook '(?i)dedicated\s+session|special-purpose\s+session' 'runbook must require a dedicated migration session'

Write-Output 'RBAC user-role SQL contract: PASS (preflight, exact manifest, NOT NULL, constraints, resumable rollback)'
