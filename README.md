# 飞驳 Feibo

飞驳健康科普内容与数字人创作平台的完整源码工作区，包含用户端、管理端、Java 后端、GEO 内容服务、Python 发布服务和静态演示项目。

本仓库为 2026-11-03 本地工作区的公开源码快照。服务凭据通过环境变量或部署环境提供；运行数据库、用户数据、登录状态、日志、备份、本地工具配置和旧 Git 历史不包含在公开快照中。

## 项目结构

| 目录 | 用途 |
| --- | --- |
| `feibo/` | React / TypeScript / Vite 用户端 |
| `feibo_backend/` | React / TypeScript / Vite 管理端 |
| `feibo_backend_master/` | Java 17 / Spring Boot 后端及数据库迁移脚本 |
| `feibo_web/` | 飞驳静态站点 |
| `geo-agentw-article-f-master/social-publish/fontback/` | Vue GEO 管理应用 |
| `geo-agentw-article-f-master/geo-agent-publish-system-master/` | GEO Java 业务模块及独立开发入口 |
| `geo-agentw-article-f-master/social-publish/` | Python 图文发布子服务 |
| `浙里健教-浙江省健康科普数智创新平台升级demo/` | 可本地打开的静态演示及公开资料来源说明 |
| `ops/`、`docs/` | 部署脚本、配置示例与技术文档 |

## 环境与构建

准备 Node.js 22.12+（也可使用符合各子项目 engines 的版本）、npm、Java 17、Maven 3.9+ 和 Python。各前端目录保留 package-lock.json，使用 `npm ci` 安装依赖。

先从仓库根目录构建 GEO 应用，脚本会将产物同步到管理端：

```sh
cd geo-agentw-article-f-master/social-publish/fontback
npm ci
npm run build:embedded
```

分别在用户端与管理端目录执行：

```sh
npm ci
npm run check
npm run build
```

用户端开发命令为 `npm run dev`，管理端为 `npm run client:dev`。GEO 的生成目录 `feibo_backend/public/geo-agent-app/` 不纳入版本管理。

在仓库根目录构建 Java reactor（包含 GEO 模块）：

```sh
mvn -f feibo_backend_master/pom.xml clean test
mvn -f feibo_backend_master/pom.xml package -DskipTests
```

Python 发布服务：

```sh
cd geo-agentw-article-f-master/social-publish
python -m venv .venv
# 激活虚拟环境后执行：
python -m pip install -r requirements.txt
python -m playwright install chromium
```

## 配置与部署

- 参考各目录的 `.env.example`、后端 `application-*.yml` 与 [GEO 集成说明](feibo_backend_master/docs/GEO_INTEGRATION.md)。数据库、Redis、对象存储、模型、支付及发布服务凭据需自行配置。
- 数据库建表与升级脚本保留在后端文档和 GEO 资源目录；不提供生产数据库导出。请阅读脚本适用前提并在自己的数据库中按需执行。
- 融合部署中，GEO 位于管理端 `/geo/*`，API 经 `/geo-agent-api/**` 转发到飞驳后端；独立 GEO 入口供兼容开发使用。
- GEO monitor、probe、auto optimization 等后台功能默认保持关闭，完成自身环境配置和验证后再启用。
- `ops/` 是原项目运维脚本和示例，部分依赖目标服务器目录、已构建归档或历史发布文件。使用前需适配自己的环境；PowerShell 部署入口要求显式传入服务器地址。
- `VITE_*` 配置会进入浏览器，长期密钥应配置在服务端。

公开源码不包含第三方账号、生产配置或真实业务验收结果。静态演示中的公开名录来源与第三方资源授权说明保留在对应目录；公开可见本身不新增软件或素材授权。
