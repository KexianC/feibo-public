# AI 视频生成首版

用户端入口：「智绘医合」左侧快捷按钮区的「AI视频」，位于「我的作品 / 我的设计 / 视频分发 / 案例视频」下方，路由 `/#/video-generation`。视频页仍归属顶部「智绘医合」导航。
管理端入口：系统管理 → AI 视频管理，路由 `/system/video-generation`（超级管理员）。

## 已实现

- Seedance、MiniMax 文生视频：提示词、模型选择、时长、清晰度、Seedance 比例设置。
- 任务数据库持久化、用户隔离、最近记录、每 10 秒查询、手动刷新、视频预览及保存链接。
- 后台展示名称、模型 ID、密钥更新、启停、调用上限及最近 100 条任务。
- 密钥使用现有 AES-GCM 加密，不返回客户端、不记录到配置操作日志；API 请求只发送至固定官方域名。
- 创建任务采用用户 + requestId 唯一约束，重复提交不重新调用供应商。网络超时保留待核查状态，不自动重发付费请求。
- 保留工作区同步加入的会员额度预占、结算和失败返还逻辑。

首版只提供文字生成视频。图片参考、首尾帧、多镜头拼接及新增供应商适配不在本次范围。

## 接入步骤

1. 在目标环境执行 `feibo_backend_master/digital-doctor-app/src/main/resources/db/video-generation.sql`。建表及默认供应商初始化可重复执行，默认两个供应商均停用。
2. 当前服务依赖工作区的会员权益模块，部署时同时应用相应权益迁移及服务。视频表的新装 schema 已含 `quota_biz_key`，升级脚本 `membership-video-enforcement.sql` 的视频部分会检查列是否存在；该脚本的数字人部分仍按原有一次性迁移管理。
3. 配置现有 `ai.model-key-encryption-secret`，保持和其他模型密钥一致的加密密钥管理方式。
4. 超级管理员进入 AI 视频管理，填写已开通的供应商模型 ID 和 API Key、设置每日上限，保存并启用。Seedance 初始模型 ID 是示例默认值，须以实际账号可用模型为准。
5. 使用有视频额度的测试账号，分别提交一条 5 秒 Seedance、6 秒 MiniMax 请求，核对供应商任务编号、结果播放、实际扣费和额度结算。这一步需要真实供应商凭据，当前尚未完成。

Seedance 首版开放 5/10 秒、720P/1080P、16:9/9:16/1:1。MiniMax 开放 6/10 秒，1080P 限 6 秒，文生视频采用默认横屏。更换模型前需确认其能力与这些参数兼容。

供应商上限按数据库日期统计；当前权益模块允许无限权益管理员绕过供应商上限和同时任务限制。后台显示的调用记录包括失败和待核查提交。

## 验证

- 两套前端的 `npm run build` 通过。
- Maven 完整依赖编译通过，22 项视频 JUnit 测试及 4 项关联会员测试全部通过。使用 H2 MySQL 模式执行真实 SQL，HTTP 供应商使用 mock，不产生费用；包括普通管理员拒绝访问、超级管理员允许访问，以及额度不足、预占、失败返还、成功结算和终态不可回退。`ops/Test-VideoGeneration.ps1` 可单独编译运行视频测试。
- 隔离浏览器测试：空提示词不可提交、示例填充、MiniMax 参数联动、提交期间禁用、状态变化、后台修改名称和停用后前台隐藏。
- 本地可交互预览：`node ops/video-generation-ui-preview.mjs`，访问 `http://127.0.0.1:5198`。可切换用户页和后台页。预览使用明确标注的模拟供应商和本地播放器测试片段，充值流程未启用；不代表真实 AI 出片。
- 验证命令：在 `feibo_backend_master` 执行 `mvn -o -pl digital-doctor-app -am test '-Dtest=VideoGenerationServiceTest,VideoProviderGatewayTest,VideoGenerationSecurityTest,ChatMembershipEnforcementTest' '-Dsurefire.failIfNoSpecifiedTests=false'`。为恢复整合验证，补齐了 `TradeController` 的 `OrderEntity` 导入，并将关联会员测试中不存在的 `Disposables.empty()` 改为 `Disposables.single()`。

## 当前运行边界

未部署生产，未写入真实供应商密钥，未进行付费生成。未把模拟接口加入生产包。

结果链接由供应商提供，可能过期，需及时保存；手动刷新可尝试重新获取链接，供应商删除结果后无法恢复。当前未自动转存至平台对象存储。

任务在用户页面打开时轮询；后台刷新读取已保存状态。待核查任务需管理员到供应商控制台核对；首版没有人工绑定上游任务编号的管理动作。

## 官方协议依据

- [Seedance 官方 SDK：创建和查询任务](https://github.com/volcengine/volcengine-python-sdk/blob/master/volcenginesdkarkruntime/resources/content_generation/tasks.py)
- [MiniMax 文生视频参数](https://platform.minimaxi.com/docs/api-reference/video-generation-t2v)
- [MiniMax 生成、查询及获取文件流程](https://platform.minimaxi.com/docs/guides/video-generation)

协议于 2026-09-09 查阅；可用模型和权限以目标供应商账号为准。
