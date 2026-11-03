# Geo Agent Publish 后端交接文档

## 1. 项目定位

本项目是“医生知识库 + 医疗内容生成 + 第三方媒体发布”的后端主服务。当前核心业务流程为：

1. 维护医院、科室、职称、医生基础资料。
2. 为医生上传资料文档，形成医生个人知识库原始文档。
3. 将医生资料解析、切块、向量化并写入 Qdrant。
4. 基于医生知识库进行 RAG 检索和流式文章/问答生成。
5. 管理生成后的文章草稿。
6. 通过 `social-publish` Python 发布服务对接第三方媒体账号登录、素材上传和文章发布。

后端服务默认端口：`8099`。

## 2. 代码结构

```text
geo-agent-publish-system-master
├─ src/main/java/com/hzmwmt
│  ├─ controller                 # 医院、科室、医生、职称、资料文档、登录等后台接口
│  ├─ service / mapper / model   # 基础管理业务、MyBatis-Plus 实体和 Mapper
│  ├─ ai
│  │  ├─ assistant               # LangChain4j Assistant 定义
│  │  └─ rag                     # 知识库解析、切块、向量化、检索、流式生成
│  ├─ a_publish                  # 对 Python 发布服务的 WebClient 封装和代理接口
│  ├─ common                     # 统一响应、异常、登录上下文
│  ├─ config                     # MVC、线程池、MyBatis-Plus 配置
│  └─ util                       # 雪花 ID、文件工具等
├─ src/main/resources
│  ├─ application.yaml           # 运行配置
│  ├─ sql/geo-agent.sql          # 当前建表 SQL
│  └─ static/index.html          # 早期静态页面，当前主要使用 Vue 前端
└─ doctor/document               # 本地上传的医生资料文件目录
```

## 3. 技术栈

- Java 17
- Spring Boot 3.2.6
- MyBatis-Plus 3.5.15
- MySQL
- LangChain4j 1.3.0-beta9
- DashScope/Qwen：`qwen-plus`、`text-embedding-v4`
- Qdrant：向量库
- WebFlux WebClient：调用 Python 第三方发布服务

## 4. 启动方式

### 4.1 基础依赖

需要先准备：

- JDK 17
- Maven
- MySQL，并创建 `geo_agent` 数据库
- Qdrant 服务
- DashScope API Key
- Python 发布服务：`../social-publish/backend_api.py`，默认监听 `5409`

### 4.2 初始化数据库

执行：

```sql
source src/main/resources/sql/geo-agent.sql;
```

当前 SQL 只包含基础资料和知识库文档相关表：

- `doctor_info`
- `hospital_info`
- `department_info`
- `title_info`
- `knowledge_document`

注意：文章管理、向量转换记录、发布账号、素材文件等表结构目前没有完整沉淀到该 SQL 中，后续交接人需要根据实体类和实际数据库补齐迁移脚本。

### 4.3 修改配置

主要配置文件：`src/main/resources/application.yaml`。

重点配置项：

- `spring.datasource.*`：MySQL 连接。
- `langchain4j.community.dashscope.*.api-key`：DashScope Key，当前文件内存在明文 Key，后续建议迁移到环境变量或配置中心。
- `storage-path`：医生资料文件保存目录。
- `app.rag.qdrant.base-url`：Qdrant 地址。
- `app.rag.qdrant.collection-name`：当前集合名 `geo_agent_knowledge`。
- `app.publish.base-url`：Python 发布服务地址，默认 `http://127.0.0.1:5409`。
- `password`：后台登录密码。

### 4.4 本地启动

```bash
mvn spring-boot:run
```

打包：

```bash
mvn clean package
```

启动 jar：

```bash
java -jar target/geo-agent-0.0.1-SNAPSHOT.jar
```

## 5. 当前已完成能力

### 5.1 登录

已完成简单后台登录：

- 单密码登录。
- Session 拦截。
- 登录状态查询。
- 登出。

相关代码：

- `LoginController`
- `LoginServiceImpl`
- `LoginInterceptor`
- `WebMvcConfig`

当前只是轻量管理后台方案，没有用户、角色、权限体系。

### 5.2 医生基础资料管理

已完成医院、科室、职称、医生的基础 CRUD、分页、统计和启停用接口。

主要模块：

- `HospitalManagementController`
- `DepartmentManagementController`
- `TitleManagementController`
- `DoctorManagementController`

医生资料表中已经关联医院、科室、职称，并包含医生头像、专长、知识库状态、启用状态、备注等字段。

### 5.3 医生资料上传

已完成按医生上传资料文档：

- 接口：`POST /document/upload`
- 参数：`file`、`doctorId`、`remark`
- 支持格式来自 `DocumentExtensionConstants.ALL_SUPPORTED_EXTENSIONS`
- 文件保存到 `storage-path`
- 文档元数据写入 `knowledge_document`

已完成资料分页查询：

- `GET /document/page`

删除接口：

- `GET /document/deleteDocument`

### 5.4 知识库向量化

已完成手动触发单文档向量化：

- `POST /rag/documents/convert?documentId=xxx`

当前处理流程：

1. 查询 `knowledge_document` 文档记录。
2. 根据文件后缀选择解析策略。
3. 抽取文本。
4. 使用 `KnowledgeChunkerImpl` 切块。
5. 调用 DashScope embedding。
6. 写入 Qdrant。
7. 更新文档状态和 chunk 数量。

主要代码：

- `RagKnowledgeBaseController`
- `RagKnowledgeBaseServiceImpl`
- `DocumentParseStrategyFactory`
- `PdfDocumentParseStrategy`
- `OfficeDocumentParseStrategy`
- `TextDocumentParseStrategy`
- `MarkdownDocumentParseStrategy`
- `HtmlDocumentParseStrategy`
- `KnowledgeChunkerImpl`
- `QdrantRestClientImpl`

### 5.5 向量检索和流式生成

已完成按医生知识库检索：

- `GET /rag/search?query=xxx&doctorId=xxx&limit=10`

已完成流式问答/文章生成入口：

- `GET /chat?userMessage=xxx&doctorId=xxx`
- 响应类型：SSE

主要代码：

- `AssistantController`
- `RagChatServiceImpl`
- `Assistant`

当前生成逻辑依赖医生知识库召回内容，适合继续扩展为“文章生成模板、平台风格、标题生成、封面建议”等更完整的创作流程。

### 5.6 文章管理

已完成文章基础管理接口：

- `POST /article/addArticle`
- `POST /article/editArticle`
- `GET /article/deleteArticle`
- `GET /article/page`

主要代码：

- `ArticleManagementController`
- `ArticleManagementServiceImpl`
- `ArticleInfo`

注意：文章表 SQL 暂未写入 `geo-agent.sql`，这是后续必须补齐的一项。

### 5.7 第三方发布服务代理

Java 后端不直接使用浏览器自动化发布，而是通过 `PublishArticleClientImpl` 调用 `social-publish/backend_api.py`。

已代理能力：

- 第三方账号登录 SSE：`GET /account/login`
- 账号列表：`GET /account/getAccounts`
- 账号校验：`GET /account/verifyAccount`
- 账号删除：`GET /account/deleteAccount`
- 素材上传：`POST /file/uploadSave`
- 素材列表：`GET /file/getFiles`
- 素材删除：`GET /file/deleteFile`
- 发布文章：`POST /postArticle/postArticle`

Python 服务当前支持平台：

- 知乎
- 小红书长文
- 今日头条文章
- 百家号图文

## 6. 主要接口清单

### 登录

- `POST /login`
- `GET /login/status`
- `POST /login/logout`

### 医院

- `POST /hospital/addHospital`
- `POST /hospital/editHospital`
- `GET /hospital/deleteHospital`
- `GET /hospital/page`
- `GET /hospital/statistics`
- `GET /hospital/toggleStatue`
- `GET /hospital/all`

### 科室

- `POST /department/addDepartment`
- `POST /department/editDepartment`
- `GET /department/deleteDepartment`
- `GET /department/page`
- `GET /department/statistics`
- `GET /department/toggleStatue`
- `GET /department/all`

### 职称

- `POST /title/addTitle`
- `POST /title/editTitle`
- `GET /title/deleteTitle`
- `GET /title/page`
- `GET /title/statistics`
- `GET /title/toggleStatue`
- `GET /title/all`

### 医生

- `POST /doctor/addDoctor`
- `POST /doctor/editDoctor`
- `GET /doctor/deleteDoctor`
- `GET /doctor/page`
- `GET /doctor/pageOther`
- `GET /doctor/statistics`
- `GET /doctor/toggleStatue`
- `GET /doctor/all`

### 医生资料与知识库

- `POST /document/upload`
- `GET /document/deleteDocument`
- `GET /document/page`
- `POST /rag/documents/convert`
- `DELETE /rag/documents/vector`
- `GET /rag/search`
- `GET /rag/documents/page`
- `GET /conversion/page`
- `GET /chat`

### 文章和发布

- `POST /article/addArticle`
- `POST /article/editArticle`
- `GET /article/deleteArticle`
- `GET /article/page`
- `GET /account/login`
- `GET /account/getAccounts`
- `GET /account/verifyAccount`
- `GET /account/deleteAccount`
- `POST /file/uploadSave`
- `GET /file/getFiles`
- `GET /file/deleteFile`
- `POST /postArticle/postArticle`

## 7. 当前进度和已知问题

### 已基本跑通

- 管理后台登录。
- 医院、科室、职称、医生基础资料维护。
- 医生资料上传和列表查询。
- 单文档向量化入库。
- 按医生知识库检索。
- SSE 流式生成。
- 文章草稿基础管理。
- 发布服务账号登录、Cookie 保存、素材和发布接口代理。

### 需要重点注意

- `application.yaml` 中存在明文数据库密码、DashScope Key、后台密码，正式交接前应更换或迁移到环境变量。
- `geo-agent.sql` 不完整，缺少文章、向量转换记录、发布相关表。
- 代码中不少中文注释出现编码异常，建议统一转换为 UTF-8。
- 登录只有单密码和 Session，没有用户体系，也没有角色权限。
- 部分接口使用 GET 执行删除、启停用等写操作，后续建议改为 RESTful 风格并补充审计日志。
- 文章生成目前更接近“流式问答/生成接口”，还没有完整的平台模板、提示词版本管理、生成记录落库闭环。
- 向量化目前主要靠手动触发，批量任务、失败重试、进度展示、重复文档识别还需要完善。
- 发布状态没有完整落库闭环，失败原因、第三方链接、重试策略仍需补齐。

## 8. 后续建议开发清单

### 8.1 数据库和迁移

- 补齐所有实际使用表的 SQL，包括文章、生成记录、向量转换记录、账号、素材、发布记录。
- 引入 Flyway 或 Liquibase 管理版本化迁移。
- 补充唯一索引和外键约束，至少保证医生、文档、文章之间的数据一致性。

### 8.2 知识库

- 支持批量上传和批量向量化。
- 支持向量化进度、失败重试、错误详情查看。
- 删除医生或文档时同步清理 Qdrant 中对应向量。
- 为 Qdrant payload 增加医生、医院、科室、文档、chunk、来源等完整字段，便于过滤和溯源。
- 增加文档去重或版本管理，避免同一资料多次上传造成召回污染。

### 8.3 内容生成

- 把 `/chat` 扩展为正式文章生成接口，支持文章类型、平台、目标人群、字数、风格、关键词等参数。
- 生成结果落库到文章表和生成记录表。
- 增加标题候选、摘要、标签、封面提示词等辅助生成能力。
- 建立提示词模板管理，避免提示词散落在代码里。

### 8.4 发布闭环

- 统一第三方平台枚举和账号模型。
- 发布请求落库，记录发布中、成功、失败、失败原因、外部链接、重试次数。
- 支持发布前账号 Cookie 校验。
- 增加发布队列，避免浏览器自动化同时发布造成冲突。
- Python 发布服务异常时 Java 侧需要更细的超时、降级和错误展示。

### 8.5 安全和运维

- 移除仓库中的密钥、密码、Cookie 文件和本地数据库文件。
- 增加多用户登录、角色权限、操作审计。
- 增加接口参数校验和统一错误码。
- 增加日志规范，区分业务日志、调用日志、发布日志。
- 增加健康检查接口和部署脚本。

### 8.6 测试

- 补充 service 层单元测试。
- 补充文档解析、向量化、Qdrant 写入和删除的集成测试。
- 补充文章生成和发布代理接口的 Mock 测试。
- 前后端联调前准备固定测试医生、测试资料和测试文章样例。

## 9. 交接人上手建议

建议按以下顺序熟悉项目：

1. 先启动 MySQL、Qdrant、Python 发布服务和 Java 后端。
2. 登录 Vue 前端，先看医院、科室、职称、医生管理。
3. 新建一个测试医生，上传一份医生资料。
4. 在知识库页面触发向量化，确认 Qdrant 中写入向量。
5. 使用 `/chat` 或前端文章生成页验证医生知识库召回和生成效果。
6. 登录一个测试媒体账号，走素材上传和文章发布流程。
7. 最后补齐数据库 SQL、配置安全和发布记录闭环。
