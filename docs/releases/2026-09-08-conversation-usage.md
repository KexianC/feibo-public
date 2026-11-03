# 对话模型、Token 与费用记录上线说明

状态：2026-09-08 12:27（北京时间）已发布，并完成公网复核。

入口：https://manage.hzmwmt.com/users/conversations

## 上线内容

- 对话列表显示实际记录的模型、平台 Token、人民币预估费用，以及已记录/已计价回复数。
- 详情按回复展示选用模型、平台返回模型、输入/输出/缓存命中/推理 Token；缓存包含在输入中，推理包含在输出中，不重复累加。
- 每次请求保存用量和价格快照，含单价、来源、版本、请求时间和适用条件。流式累计用量采用快照替换，不把重复累计值相加。
- 历史记录缺少平台用量时显示未记录，保留原文本估算说明，但不将其用于平台费用汇总。未计价回复不进入费用合计，并显示计价覆盖数量。
- 对话与模型长 ID 保持完整精度；详情请求切换时阻止旧响应覆盖新内容。
- 新增 nullable JSON 列 t_ai_message.usage_json；没有回填或改写历史用量。

## 计价范围与限制

金额是参考官方公开价格计算的 Token 推理费估算，不是平台钱包实际扣款，也不会改变现有用户额度扣费逻辑。本次未新增平台钱包余额查询或账单对账。

已接入下列匹配的官方端点和模型，均以人民币/百万 Token 计算：

| 平台 | 模型 | 官方来源 |
| --- | --- | --- |
| DeepSeek | deepseek-v4-flash / deepseek-v4-pro，按请求时间区分峰谷 | https://api-docs.deepseek.com/zh-cn/quick_start/pricing/ |
| 阿里云百炼北京 | qwen3.8-max、qwen3.7-plus、qwen3.7-flash | https://help.aliyun.com/zh/model-studio/model-pricing |
| 火山方舟 | doubao-seed-2-1-pro-260628 | https://www.volcengine.com/product/doubao |
| Kimi 国内 | kimi-k3 | https://platform.kimi.com/docs/pricing/chat-k3 |
| 腾讯 TokenHub | hy3 | https://cloud.tencent.com/document/product/1823/130055 |
| 百川 | Baichuan-M3-Plus | https://platform.baichuan-ai.com/prices |
| 百度千帆 | ernie-5.1 / ernie-5.0 | https://cloud.baidu.com/doc/qianfan/s/wmh4sv6ya |

价格快照版本为 official-2026-09-08-v1，不会自动联网更新。qwen3.7-plus 使用公开原价，不抵扣促销折扣。qwen3.8-max 缓存命中单价未确认，存在缓存或缓存计数缺失时不计价。缓存有独立价格的平台必须取得缓存计数才能计价；无独立缓存折扣的公开单价按全部输入计算，但仍保留缓存计数未知状态。

GLM-5.3、ernie-4.5-turbo-128k 及未确认价格的医疗/私有模型目前显示价格待确认。资源包、赠送额度、合同折扣、工具搜索、缓存存储、语音等附加费用未计入。异常或取消请求沿用现有历史保存流程，未新增独立的失败请求计费台账。

## 验证证据

- 13 项相关 Java 测试通过：计费与原生流式用量 6 项、历史汇总 2 项、数据库模型选择 5 项。TypeScript 类型检查通过。
- 在服务器以候选 JAR 单独启动隔离实例，关闭 WebSocket 和定时任务，执行真实 qwen3.7-flash 调用。
- 完整 AIPort 请求链路成功自动捕获并计算费用；历史列表与详情金额一致；真实用量保存后可读取，多轮累计准确，原有历史读取正常。
- 验证数据在同一数据库事务内回滚，未留下测试对话。
- 浏览器使用明确标注的本地测试样例验证列表、详情、计费依据展开、历史未知数据和长 ID。修复验证发现的 JSX 运行时兼容问题。
- 正式公网管理页面 HTTP 200，加载新资源；资源 SHA-256 与候选完全一致。模型接口及后台详情接口未登录均 HTTP 401。正式浏览器显示登录页；未进行已登录生产账号的手动验收。

## 发布与回退

仅替换 15 个白名单 Java 源文件产生的 19 个 class，位于 domain/infrastructure 两个内部 JAR。其他 JAR 条目保持原内容。前端只替换线上包中的对话组件及其新服务依赖；组件外前缀、后缀保持相同。方法级复核排除了编译常量池、匿名函数编号与重复函数合并噪声；原历史路径通过实际存取验证。

- 旧后端 SHA-256：2898ea56c39e0b7033c15171bcf671852a0a2e39960afb24d9b18a545d3751b9
- 新后端 SHA-256：d930ffc16869e9db702c604ea743701f5a26e76c293cdca1a4a7c6a04a2f2f22
- 新前端 JS SHA-256：6d2a6d14b7283a87aa7c31c0f694b20b572ffdda5f5676f6bd3e24ae0ebd8054
- 前端版本：/www/wwwroot/manage.hzmwmt.com/releases/conversation-usage-20260908-122745
- 服务器备份：/www/wwwroot/deploy-backups/conversation-usage-20260908-122745
- 服务器发布记录：/root/codex-feibo-conversation-usage/20260908/deployment-result.json

正式切换前备份了原 JAR、运行参数和前端指针，敏感运行参数仅留在服务器受保护目录。发布脚本具备失败回滚。应用配置和患者端发布指针未变；切换后宝塔监控已恢复。回退应用版本时保留兼容的 nullable usage_json 列，不应删除已产生的新用量快照。
