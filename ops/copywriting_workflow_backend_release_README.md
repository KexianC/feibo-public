# 智核医审后端候选制包与切换

本流程只把 `ops/copywriting_workflow_release_files.txt` 中的后端运行时白名单编译产物注入线上基线 JAR。它不会重新打包或带入工作区的其他改动。

## 1. 本地制包

在工作区根目录运行：

```powershell
& .\ops\build_copywriting_workflow_backend_candidate.ps1
```

默认输出目录：

```text
release/copywriting-workflow-20260812-1615/artifacts/backend-candidate/
```

包装脚本持有本地独占构建锁，并自行在隔离发布树运行固定 Maven `clean package` 与 3 项 AI 审核测试，不信任已有 `target/classes`。制包器的硬门禁包括：完整生产基线 SHA、固定白名单 SHA、53 个运行时 Java 源对应 68 个唯一 class（含全部 `$` 内部类）、9 个 XML、四个业务 nested JAR 和外层 JAR 的双向逻辑 diff、逐 entry 差异清单、21 个既有生产热补丁 class、dev/prod 配置、签名与 multi-release 覆盖、重复/大小写冲突、全部 `BOOT-INF/lib/*.jar` 的 local/central Stored 方法以及 ZIP 完整性。输出目录必须此前不存在。

## 2. 远端切换

先在独立数据库关卡完成备份、三份迁移及校验，并准备一个 root 所有、权限 `700`、内容 SHA 已记录的无交互数据库回滚 hook。hook 接收唯一参数 `automatic-backend-release-failure`，成功恢复并验证迁移前数据库状态时返回 0，否则必须返回非 0。

把候选 JAR、两个 manifest、回滚 hook 和发布脚本上传到 root-only 目录后，先在服务器执行：

```bash
bash -n deploy_copywriting_workflow_backend_remote.sh
```

再执行（SHA 必须是回滚 hook 的完整小写 SHA-256）：

```bash
bash deploy_copywriting_workflow_backend_remote.sh \
  /absolute/path/digital-doctor-app.copywriting-workflow.candidate.jar \
  /absolute/path/injected-entries.sha256 \
  /absolute/path/protected-hotpatch.sha256 \
  /absolute/release/version/database/process \
  /absolute/release/version/database/recovery/rollback.sh \
  schema-migrated-and-rollback-ready
```

脚本不执行正向数据库迁移。它持有与数据库关卡相同的全局发布锁，固定生产基线/候选 SHA，验证数据库关卡的 `apply.complete`、dump、SQL、process capture、recovery manifest/hook 与 JAR manifest 的哈希绑定，确认 8091 和应用进程仍处于停止状态，读取数据库关卡保存的 cmd/env/cwd/日志，备份并在同文件系统原子替换 JAR，复用原启动上下文，并以后台受保护接口返回 401 作为判活证据。

若切换失败，脚本先调用数据库回滚 hook；只有数据库成功回到迁移前状态后，才会原子恢复旧 JAR 并拉起旧进程。数据库回滚失败时不会启动与新数据库状态不兼容的旧代码，并会输出 `CRITICAL` 告警。
