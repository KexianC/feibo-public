# GEO Agent 与飞驳融合说明

## 架构

融合只保留一份 GEO Vue 源码，并将其作为 standalone 应用交付给管理端。GEO 共用飞驳 JWT 和统一后端，不维护独立登录态。

```text
GEO Vue 源码（fontback）
  └─ standalone -> feibo_backend/public/geo-agent-app
       └─ 管理端 /geo/* -> standalone hash route

管理端 GEO 应用
  └─ /geo-agent-api/** + 飞驳 JWT
       └─ digital-doctor-app :8091
            └─ digital-doctor-geo（前缀、权限、请求身份、JSON 适配）
                 └─ geo-agent（GEO 业务）
```

GEO 的可执行包使用 `exec` classifier；飞驳只依赖普通 GEO jar，避免嵌套可执行 jar 和第二个 Spring Boot 入口污染宿主。

## 前端入口与构建产物

| 功能 | 宿主入口 | 静态入口 | 集成方式 |
| --- | --- | --- | --- |
| 医务管理（内含医院、科室、医生三个页签） | `/geo/medical-management` | `/geo-agent-app/index.html#/medicalManagement` | standalone GEO 应用 |
| 知识库管理 | `/geo/knowledge-base` | `/geo-agent-app/index.html#/knowledgeBase` | standalone GEO 应用 |
| GEO运营 | `/geo/workbench` | `/geo-agent-app/index.html#/geoWorkbench` | standalone GEO 应用 |
| 内容发布 | `/geo/content-publish` | `/geo-agent-app/index.html#/contentPublish` | standalone GEO 应用 |

旧的 `/geo/hospital-management`、`/geo/department-management`、`/geo/doctor-management` 仅作为历史直达路径保留，用于兼容已有书签和链接，不再显示在管理后台菜单中。

构建及同步命令：

```powershell
Set-Location .\geo-agentw-article-f-master\social-publish\fontback

# 构建 standalone 应用，并只同步到管理端
npm run build:embedded
```

- `build:embedded` 生成普通 `dist`，随后由 `scripts/sync-embedded.mjs` 只替换 `feibo_backend/public/geo-agent-app`。
- 不要直接修改 `feibo_backend/public/geo-agent-app` 中的生成文件。

## 认证与接口

- API 根路径：`/geo-agent-api`
- 认证头：`Authorization: Bearer <access-token>`
- 设备头：`client-fingerprint`
- 管理端 GEO 使用飞驳统一 JWT。GEO 收到 401 时按管理端统一登录流程处理，不维护独立登录态。
- 统一后端要求 `ROLE_SUPER_ADMIN`。`GeoHostAuthenticationInterceptor` 在每个 `/geo-agent-api/**` 请求中校验 Spring Security authentication，并将 JWT principal 的用户 ID、手机号、昵称和管理员身份映射到 GEO `LoginContextHolder`。
- `LoginContextHolder` 是请求级 `ThreadLocal`：请求完成或进入异步处理后立即清理。GEO 业务不得使用前端提交的用户 ID 判断当前身份。
- 调用 Python 发布服务时，`PublishArticleClientImpl` 从当前请求的 `LoginContextHolder` 取得用户 ID，并在该次下游请求中设置 `userid` 头；并发用户之间没有共享的静态用户 ID。
- 无 token 返回 401；已登录非管理员返回 403。
- 融合模式禁用 GEO `/login` 和 `/system` 控制器，用户与角色只使用飞驳 `t_user/t_role/t_user_role_map`。
- GEO 模型中的 `Long` ID 定向序列化为字符串，避免 JavaScript 丢失 Snowflake ID 精度；飞驳其他 DTO 不受影响。

GEO 独立运行时可设置 `VITE_GEO_AUTH_MODE=session` 和独立 API 地址，保留原 session/CSRF 流程。

## 数据库

统一进程只使用飞驳 datasource。GEO SQL 已去除 `CREATE DATABASE` 和 `USE geo_agent`，执行前必须主动选择目标飞驳 schema。

### 全新 schema（clean install）

备份并选中目标 schema 后，按以下顺序执行：

1. `geo-agent-publish-system-master/src/main/resources/sql/geo-agent.sql`
2. `geo-agent-publish-system-master/src/main/resources/sql/article-extra.sql`
3. `geo-agent-publish-system-master/src/main/resources/sql/geo-feature.sql`

第一份脚本创建医院、科室、医生、知识文档、向量转换和审计基础表；第二份创建文章表；第三份增加 GEO 监测、证据、合规、建议及增量字段。

### 已有 GEO schema（existing upgrade）

`CREATE TABLE IF NOT EXISTS` 不会修正已有表，所以不能只重跑 clean-install 基线。停止写入、做全量备份并在 staging 恢复验证后，按以下顺序执行：

1. `geo-agent-publish-system-master/src/main/resources/sql/geo-agent.sql`（只补齐缺失的基础表）
2. `geo-agent-publish-system-master/src/main/resources/sql/article-extra.sql`（只补齐缺失的文章表）
3. `geo-agent-publish-system-master/src/main/resources/sql/geo-existing-upgrade.sql`（无损、可重复的旧库结构升级）
4. `geo-agent-publish-system-master/src/main/resources/sql/geo-feature.sql`（GEO 功能表及其余增量列）

`geo-existing-upgrade.sql` 会补齐 `department_info` 的医院范围列，添加 `knowledge_document.remark`，并保留旧 `file_update_time` 的数据、仅将其改为可空，避免新代码插入文档时被旧 NOT NULL 约束拦截。它还会创建或补齐 `knowledge_vector_conversion`，并将后续转换记录的 `status` 默认值统一为 `3`（处理中）；既有状态值不会被重映射。脚本末尾的诊断计数如果非零，必须人工回填并复核，脚本不会伪造或删除旧 ID。

所有脚本都使用 `DATABASE()` 判断目标列，因此不可在未选择 schema 的连接中执行。当前项目没有 Flyway/Liquibase，SQL 不会随启动自动执行。生产迁移必须核对表结构、行数、空值与孤儿引用后再上线。融合模式不会使用 `system_user` 或 `system_setting`，但审计仍使用 `system_operation_log`。

## 配置

飞驳主 `application.yml` 显式设置 `app.geo-integration.enabled=true`，并以必需 classpath import 加载 `digital-doctor-app/src/main/resources/geo-agent-integration.yml`；集成配置漏打包时应立即启动失败，不可退回到无前缀的独立登录模式。常用环境变量：

| 环境变量 | 用途 | 默认行为 |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 飞驳与 GEO 共用 datasource | **启动必填** |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | 飞驳对象存储 | **启动必填** |
| `REDIS_HOST` / `REDIS_USERNAME` / `REDIS_PASSWORD` | 飞驳 Redis | host 必填；用户名、密码可为空 |
| `APP_STORAGE_PATH` | GEO 文档目录 | `${user.home}/geo-agent/documents` |
| `APP_MAX_UPLOAD_BYTES` | 最大上传字节数 | 20 MiB |
| `DASHSCOPE_API_KEY` | 对话与 embedding | **启动必填**；缺失时 LangChain4j 模型初始化会使应用启动失败 |
| `QDRANT_BASE_URL` / `QDRANT_API_KEY` | 向量库 | 本机 `6333` / 空 |
| `PUBLISH_SERVICE_BASE_URL` / `PUBLISH_INTERNAL_TOKEN` | 发布服务 | 本机 `5409` / 空 |
| `FFMPEG_PATH` | 飞驳音视频处理程序 | 使用系统 `PATH` 中的 `ffmpeg` |
| `GEO_MONITOR_ENABLED` | 定时监测 | `false` |
| `GEO_PROBE_ENABLED` | 对话探针 | `false` |
| `GEO_AUTO_OPTIMIZATION_ENABLED` | 自动生成优化建议 | `false` |

图文媒体账号与发布功能还需要启动 `geo-agentw-article-f-master/social-publish/backend_api.py`。首次运行先安装 `social-publish/requirements.txt`；`SOCIAL_PUBLISH_BASE_DIR` 必须指向独立的运行数据目录，服务会在其中自动创建 SQLite、Cookie、图片和日志目录，不应直接复用源码目录中的历史数据。Python 服务只信任 Java 统一后端传入的 `userid`，账号、Cookie、素材、登录 SSE 队列及发布动作都会按该用户隔离。

生产环境必须为 Java 与 Python 配置同一个非空 `PUBLISH_INTERNAL_TOKEN`，并让 Python 仅监听内网或 loopback 地址。示例：

```powershell
Set-Location .\geo-agentw-article-f-master\social-publish
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m playwright install chromium
$env:SOCIAL_PUBLISH_BASE_DIR = 'C:\geo-agent\social-publish-data'
$env:PUBLISH_INTERNAL_TOKEN = '<与 Java 进程一致的随机密钥>'
.\.venv\Scripts\python.exe .\backend_api.py
```

独立 GEO 使用 `geo-agent-standalone.yml`，由 `GeoAgentApplication` 以必需 classpath import 显式导入（不再是 `optional`），且该文件显式设置 `app.geo-integration.enabled=false`。独立登录、session 拦截器只在这个明确状态下启用；缺少属性不会在飞驳宿主中意外开启它们。该配置不会作为依赖污染飞驳的端口、datasource、session 或 Jackson 设置。

飞驳 dev/prod YAML 的历史明文凭据在当前版本已改为环境变量占位；数据库、Redis、MinIO、JWT RSA、支付、数字人和各 AI provider 密钥必须由部署环境注入。由于基线 commit 曾保存旧值，任何推送到远程前都必须轮换这些凭据，并评估清理 Git 历史。

## 业务与安全不变量

- 医疗内容必须先通过人工审核和医疗合规审核才可发布。
- 证据优先于模型诊断；模型结论不能覆盖提及、引用和官方来源证据。
- 医院官网只生成 `recommendation_only` 建议，不自动写站或自动发布。
- 文件上传继续执行扩展名、大小、文件名清理、规范化路径和目录包含检查。
- monitor、probe、来源观察和官网建议保留为独立证据链。
- 多实例任务领取继续使用条件更新的原子 claim。
- 发布子服务不得接受浏览器声明的用户身份；每个账号、Cookie、素材和发布动作必须继续由 Java 从 JWT 映射出的 `userid` 约束。

## 验收

1. 在 GEO 前端运行 `npm run build:embedded`，确认管理端 standalone 产物完整。
2. 分别运行飞驳用户端和管理端的 type-check/build。确认用户端没有 GEO 菜单和 `/geo-agent` 路由，管理端 `/geo/*` 页面可正常加载。
3. 验证管理端 Token 刷新后 GEO 请求继续成功，失效会话按统一登录流程处理。
4. 在 `feibo_backend_master` 根运行完整 Maven reactor tests，覆盖管理员 200、非管理员 403、未认证 401、请求完成清理 `LoginContextHolder`，以及两个用户连续发布时分别透传各自 `userid`。
5. 检查最终 `digital-doctor-app.jar` 只包含 GEO 普通 jar，不包含 `geo-agent-*-exec.jar`。
6. 使用空的 `SOCIAL_PUBLISH_BASE_DIR` 启动 `5409`，验证两个用户看不到彼此的媒体账号、Cookie 和素材，伪造 Cookie/素材路径及跨用户发布均被拒绝。
7. 在迁移后的 staging 库启动 `8091`，验证字符串 ID 往返、PDF/Word 上传、路径穿越拒绝、Qdrant 故障降级、医疗合规拦截和 `recommendation_only`。
8. 最后分阶段开启 probe、monitor；auto optimization 保持最后开启。
