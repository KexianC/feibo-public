# RBAC 用户角色映射约束迁移手册

## 目标与边界

本迁移为 `t_user_role_map` 建立四个数据库不变量：

- `user_id`、`role_id` 物理列均为 `NOT NULL`；
- `(user_id, role_id)` 唯一，禁止同一角色被重复授予；
- `user_id -> t_user.user_id`、`role_id -> t_role.role_id` 是当前 schema 内的外键；
- 外键使用 `ON UPDATE RESTRICT ON DELETE RESTRICT`，权限不会被级联静默改变。

项目没有 Flyway/Liquibase，`docs/sql` 脚本不会随应用启动执行。本次只提供可审计的迁移资产，没有连接或修改生产数据库。

已有生产只读快照为：孤儿用户引用 6 行、孤儿角色引用 1 行、重复映射 0 组。两个孤儿集合可能重叠，且上线前数据会变化，因此脚本没有内置这些数字，也绝不按“数量相同”推断审批仍然有效。审批对象必须是新鲜预检输出的每个 `id + source_row_sha256`。

## 安全状态机

```text
00 只读预检（输出逐行证据；脏数据或可空列均失败）
  -> 人工审批精确 ID + SHA-256 清单
10 隔离并清理（QUARANTINED）
  -> 00 再审计：重复/孤儿/NULL 行必须为 0；列可空时仍失败
15 按实时 COLUMN_TYPE 显式收紧 NOT NULL
   NOT_NULL_HARDENING -> NOT_NULL_HARDENED
  -> 00 必须返回 READY_FOR_CONSTRAINT_MIGRATION
20 添加唯一约束、索引、同 schema 外键
   CONSTRAINTS_APPLYING -> CONSTRAINTS_APPLIED
```

10 将获批行的原始值、原因、逐行 SHA-256、批准变更单、执行账号和时间永久写入 `t_user_role_map_quarantine`，并在 `t_rbac_user_role_constraint_migration` 保存整个有序清单的聚合 SHA-256。15、20、90 都重新计算逐行哈希和聚合哈希；任一隔离行、变更单或账本被改动都会失败关闭。

15、20、90 在隐式提交 DDL 前先提交 pending 状态和本批所有权标记。客户端在 DDL 后断线时，使用原批次、原变更单和原计数重新运行同一脚本；脚本只接受“全部未发生”或“精确定义已全部发生”，部分状态及定义漂移必须人工评审。90 可从 `QUARANTINED`、`NOT_NULL_HARDENING`、`NOT_NULL_HARDENED`、`CONSTRAINTS_APPLYING` 或 `CONSTRAINTS_APPLIED` 任一持久状态开始撤销。

## 强制上线条件

1. 仅支持 Oracle MySQL 8+；MariaDB 会被脚本拒绝。先在生产结构副本的 staging 完整演练执行、断线重试和回滚。
2. 对 `t_user_role_map`、`t_user`、`t_role`、迁移账本和 quarantine 做可恢复备份。
3. 安排维护窗口，停止应用写入，并在数据库侧临时撤销应用账号对 `t_user_role_map` 的 `INSERT/UPDATE/DELETE`。`GET_LOCK` 只能串行化迁移操作员，不能阻止应用连接。回滚在恢复前可能重新允许 NULL，唯一索引也无法阻止重复的 NULL 组合，因此数据库写屏障是硬条件。
4. 使用一个专用的 mysql 连接（dedicated session）完成每个阶段；不要经过会重连或连接池化的代理。精确审批清单是 `TEMPORARY TABLE`，重连后会消失并令脚本失败。
5. 每个脚本执行前都显式 `USE` 目标库并设置完全相同、区分大小写的 `@rbac_expected_schema`。变更脚本会在任何持久表/过程 DDL 前通过会话临时表校验该值，选错库不会留下迁移对象。
6. 五张相关表必须是 InnoDB，映射表、迁移账本和 quarantine 上不能有未评审触发器。应用账号不得拥有迁移账本或 quarantine 的 `UPDATE/DELETE` 权限。
7. 保存完整标准输出、错误输出和 `SHOW WARNINGS` 到变更单，不要把数据库密码写入命令历史或日志。
8. 禁止 `mysql --force`。任何错误必须让部署任务立即退出，不能继续 source 下一个文件。

## 执行顺序

下列片段都在已认证的专用 mysql 会话执行；占位符必须替换，不能原样运行。

### 1. 只读预检并取得证据

```sql
USE `<target_schema>`;
SET @rbac_expected_schema = '<target_schema>';
SOURCE feibo_backend_master/docs/sql/rbac_user_role_map_constraints_00_preflight.sql;
```

预检只使用只读事务和会话 `TEMPORARY TABLE` 断言，不写持久 schema。记录以下证据：

- 父键和映射键的真实 `COLUMN_TYPE`；
- 映射键 `IS_NULLABLE`、default、extra；
- `duplicate_mapping_groups`、`orphan_user_rows`、`orphan_role_rows`、`orphan_distinct_rows`；
- `null_mapping_key_rows`、空/重复源 `id`；
- 每条孤儿的 `id/user_id/role_id/create_time/blocker_reason/source_row_sha256`。

存在孤儿、重复、NULL 行，或映射键仍允许 NULL 时，脚本在输出证据后以错误结束，这是预期行为。重复映射不能由本迁移自动择一保留，必须另开处置审批。

### 2. 用精确 ID/哈希清单隔离

业务负责人逐条批准隔离后，在同一连接创建临时清单。下面只有表结构是固定模板，`VALUES` 必须逐行复制本次预检证据：

```sql
USE `<target_schema>`;
SET @rbac_expected_schema = '<target_schema>';
SET @rbac_migration_batch_id = UUID();
SET @rbac_change_ticket = 'CHANGE-123456';
SET @rbac_expected_duplicate_groups = 0;
SET @rbac_expected_orphan_user_rows = <fresh orphan_user_rows>;
SET @rbac_expected_orphan_role_rows = <fresh orphan_role_rows>;
SET @rbac_expected_orphan_distinct_rows = <fresh orphan_distinct_rows>;

CREATE TEMPORARY TABLE `_rbac_approved_orphan_manifest` (
    `source_id` BIGINT NOT NULL,
    `source_row_sha256` CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (`source_id`)
) ENGINE=InnoDB;

INSERT INTO `_rbac_approved_orphan_manifest`
    (`source_id`, `source_row_sha256`)
VALUES
    (<approved id>, '<approved lowercase sha256>');

SELECT @rbac_migration_batch_id AS migration_batch_id;
SOURCE feibo_backend_master/docs/sql/rbac_user_role_map_constraints_10_quarantine_cleanup.sql;
```

10 在一致性事务中锁定获批源行，要求当前完整孤儿集合与清单逐个 ID/哈希完全相等，将这些行复制进 quarantine，然后只按本批 quarantine 的主键和全部原值删除。新增孤儿替换旧孤儿、同数换行、哈希变化、清单遗漏/扩展、插入数和删除数不同都会回滚应用数据变更。

`CREATE TABLE IF NOT EXISTS` 本身是隐式提交 DDL：首次失败时，空的审计表/quarantine 表可能保留，这是预期的可重用迁移基础设施；它们必须通过脚本的列、键、引擎和触发器契约后才会处理映射行。

保存批次 UUID，并核对：

```sql
SELECT *
FROM t_rbac_user_role_constraint_migration
WHERE migration_batch_id = @rbac_migration_batch_id;

SELECT source_id, source_user_id, source_role_id, source_create_time,
       orphan_reason, source_row_sha256, approved_change_ticket,
       quarantined_at, quarantined_by
FROM t_user_role_map_quarantine
WHERE migration_batch_id = @rbac_migration_batch_id
ORDER BY source_id;
```

### 3. 清理后再预检

重新设置 `@rbac_expected_schema` 并运行 00。重复、孤儿、NULL 行必须全为 0。如果列仍为 `IS_NULLABLE=YES`，最后的失败仍是预期结果；此时可以进入 15，但不能跳到 20。

### 4. 独立收紧 NOT NULL

```sql
USE `<target_schema>`;
SET @rbac_expected_schema = '<target_schema>';
SET @rbac_migration_batch_id = '<阶段 2 批次 UUID>';
SET @rbac_change_ticket = 'CHANGE-123456';
SET @rbac_expected_quarantined_rows = <ledger quarantined_rows>;
SOURCE feibo_backend_master/docs/sql/rbac_user_role_map_constraints_15_harden_not_null.sql;
```

15 从 `information_schema.columns` 读取并记录两个键的实时完整 `COLUMN_TYPE` 和注释，先断言没有任何 NULL 映射，再动态生成 `MODIFY COLUMN ... <原 COLUMN_TYPE> NOT NULL`。脚本没有硬编码 `BIGINT`。若列是带字符集/排序规则的字符类型，或已有 default/extra 等不能由当前账本完整记录并无损还原的定义，脚本停止并要求人工生成、评审独立 DDL，避免 `MODIFY COLUMN` 意外改变未写出的列属性。

完成后查询 `information_schema.columns`，两个 `IS_NULLABLE` 必须均为 `NO`，然后再次运行 00；只有出现 `READY_FOR_CONSTRAINT_MIGRATION` 才能继续。

### 5. 添加最终约束

```sql
USE `<target_schema>`;
SET @rbac_expected_schema = '<target_schema>';
SET @rbac_migration_batch_id = '<阶段 2 批次 UUID>';
SET @rbac_change_ticket = 'CHANGE-123456';
SET @rbac_expected_quarantined_rows = <ledger quarantined_rows>;
SOURCE feibo_backend_master/docs/sql/rbac_user_role_map_constraints_20_apply.sql;
```

20 只接受 `NOT_NULL_HARDENED`。缺失对象通过单条原子 `ALTER TABLE` 添加：

- `uk_t_user_role_map_user_role (user_id, role_id)`；
- `idx_t_user_role_map_role_id (role_id)`；
- `fk_t_user_role_map_user`；
- `fk_t_user_role_map_role`。

既有语义等价对象可以复用；规范名称一旦存在，必须同时匹配列顺序、唯一性、BTREE、可见性、无前缀、升序以及同 schema 外键目标和 RESTRICT 规则，否则停止。另一 schema 中同名父表的外键不算等价。

## 验收

```sql
SELECT column_name, column_type, is_nullable, column_default, extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_user_role_map'
  AND column_name IN ('user_id', 'role_id')
ORDER BY ordinal_position;

SELECT user_id, role_id, COUNT(*) AS duplicate_rows
FROM t_user_role_map
GROUP BY user_id, role_id
HAVING COUNT(*) > 1;

SELECT
    SUM(user_record.user_id IS NULL) AS orphan_user_rows,
    SUM(role_record.role_id IS NULL) AS orphan_role_rows
FROM t_user_role_map AS role_map
LEFT JOIN t_user AS user_record ON user_record.user_id = role_map.user_id
LEFT JOIN t_role AS role_record ON role_record.role_id = role_map.role_id;

SELECT constraint_name, column_name, referenced_table_schema,
       referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE constraint_schema = DATABASE()
  AND table_name = 't_user_role_map'
ORDER BY constraint_name, ordinal_position;

SELECT *
FROM t_rbac_user_role_constraint_migration
WHERE migration_batch_id = @rbac_migration_batch_id;
```

仅在 staging 或可回滚测试数据上做负向验证：`NULL` 键、重复授权、不存在用户、不存在角色都应被数据库拒绝；合法授权和撤销应成功。

## 断线和重试

MySQL DDL 隐式提交，不能把 15、20、90 包在一个事务里获得跨 DDL 回滚。出现连接中断时：

- `NOT_NULL_HARDENING`：用原变量重新运行 15；
- `CONSTRAINTS_APPLYING`：用原变量重新运行 20；
- `ROLLBACK_DROPPING_CONSTRAINTS`、`ROLLBACK_CONSTRAINTS_DROPPED`、`ROLLBACK_FINALIZING_INDEXES`：用原回滚票据重新运行 90。

不要手工改状态或所有权位。脚本会精确比对实际列、索引和外键；DDL 全部已提交时只完成账本，全未发生时重做，部分发生或同名对象被替换时失败关闭。

## 紧急回滚

90 可直接撤销任一已落账阶段：仅完成隔离、NOT NULL 计划/DDL、apply 计划，或已完成最终约束。对于 `NOT_NULL_HARDENING` / `CONSTRAINTS_APPLYING`，它同样按“DDL 全未发生或精确全部发生”对账；检测到原子 DDL 的部分状态、同名替换或其他外键时停止并要求 DBA 评审。

再次停止应用并在数据库侧撤销映射表写权限，然后执行：

```sql
USE `<target_schema>`;
SET @rbac_expected_schema = '<target_schema>';
SET @rbac_migration_batch_id = '<已应用批次 UUID>';
SET @rbac_rollback_ticket = 'INCIDENT-123456';
SET @rbac_expected_quarantined_rows = <ledger quarantined_rows>;
SET @rbac_confirm_writes_blocked = 1; -- 仅在数据库写权限已撤销后设置
SOURCE feibo_backend_master/docs/sql/rbac_user_role_map_constraints_90_rollback.sql;
```

`@rbac_confirm_writes_blocked=1` 是强制的 fail-closed 操作员确认，不会替代实际权限撤销；若只设置变量而应用仍能写入，回滚仍不安全。

90 先把早期阶段缺省的所有权位正规化为显式 0，再只删除账本标记为本批创建且精确定义仍一致的对象。执行顺序为：

1. 写入 `ROLLBACK_DROPPING_CONSTRAINTS`；
2. 删除本批外键，并仅把本批收紧的列恢复为账本记录的原类型/注释/可空状态；
3. 写入 `ROLLBACK_CONSTRAINTS_DROPPED`；
4. 在事务中检查主键和键对冲突，恢复 hash 校验通过的原始行；唯一索引在恢复提交前始终保留；
5. 同一事务写入 `ROLLBACK_FINALIZING_INDEXES`，提交后才删除本批唯一/角色索引；
6. 写入 `ROLLED_BACK`，quarantine 永久保留并记录恢复人、时间和事故单。

恢复的行本来就是孤儿，因此外键必须先移除。回滚后的库会重新处于已知不一致状态，必须限制访问并安排后续修复。

## 静态契约测试

不连接数据库的快速检查：

```powershell
& .\feibo_backend_master\docs\sql\test_rbac_user_role_map_constraints.ps1
```

该测试覆盖精确审批清单、NOT NULL 独立阶段、同 schema 外键、DDL pending/reconcile、定义指纹、哈希复核和回滚顺序。上线前仍必须在 MySQL 8 staging 完成真实语法、锁、断线和攻击场景测试；静态检查不能代替数据库演练。
