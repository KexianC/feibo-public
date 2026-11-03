# Geo Agent Publish 前端交接文档

## 1. 项目定位

`fontback` 是 Geo Agent Publish 的 Vue 管理后台，主要服务于医生资料管理、医生知识库建设、文章生成和第三方媒体发布流程。

当前前端默认通过 Vite 代理访问 Java 后端：

- 前端开发端口：`5173`
- 前端 API 前缀：`/api`
- Java 后端地址：`http://localhost:8099`
- Python 发布服务由 Java 后端转发调用，默认地址：`http://127.0.0.1:5409`

## 2. 技术栈

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- pinia-persistedstate-plugin
- Element Plus
- Axios
- event-source-polyfill

## 3. 目录结构

```text
fontback
├─ src
│  ├─ api                    # 后端接口封装
│  ├─ assets                 # 全局样式
│  ├─ router                 # 路由配置
│  ├─ stores                 # Pinia 状态
│  ├─ views                  # 业务页面
│  ├─ App.vue
│  └─ main.ts
├─ vite.config.ts            # Vite 配置和 /api 代理
├─ package.json
└─ README.md
```

## 4. 启动方式

### 4.1 安装依赖

```bash
npm install
```

### 4.2 启动开发环境

```bash
npm run dev
```

访问：

```text
http://localhost:5173
```

### 4.3 构建

```bash
npm run build
```

构建前会执行 `vue-tsc --build` 类型检查。

### 4.4 预览构建产物

```bash
npm run preview
```

## 5. 本地联调依赖

前端单独启动不能完整跑通业务，需要同时启动：

1. Java 后端：`geo-agent-publish-system-master`，默认 `8099`。
2. Python 发布服务：`social-publish/backend_api.py`，默认 `5409`。
3. MySQL：保存医生、文档、文章等业务数据。
4. Qdrant：保存医生知识库向量。
5. DashScope Key：用于文章生成和向量化。

Vite 代理配置在 `vite.config.ts`：

```ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8099',
      changeOrigin: true,
      rewrite: path => path.replace(/^\/api/, '')
    }
  }
}
```

## 6. 页面和功能进度

### 6.1 已接入后端的页面

- `Login.vue`：后台登录。
- `MainLayout.vue`：后台主布局和菜单。
- `HospitalManagement.vue`：医院管理。
- `DepartmentManagement.vue`：科室管理。
- `TitleManagement.vue`：职称管理。
- `DoctorManagement.vue`：医生管理。
- `KnowledgeBase.vue`：医生知识库页面，包含医生筛选、资料上传、资料列表、向量化操作。
- `VectorConversion.vue`：向量转换记录/知识库转换辅助页面。
- `AccountManagement.vue`：第三方媒体账号管理，调用登录、校验、删除账号接口。
- `ArticleGenerator.vue`：文章生成页面，已接入医生选择和生成入口。
- `ArticleManagement.vue`：文章管理页面，部分数据和操作已和接口对齐。

### 6.2 仍偏静态或需要继续接接口的页面

- `CoverManagement.vue`：当前主要使用 `picsum.photos` 示例图片，封面素材上传、列表、删除和选用流程需要接真实接口。
- `PublishRecord.vue`：当前是静态发布记录示例，需要接发布记录表和后端接口。
- `Profile.vue`：当前偏静态个人中心。
- `Register.vue`：当前存在页面，但后端没有完整注册/多用户体系。

## 7. API 封装说明

统一 Axios 实例：`src/api/interceptor.ts`。

当前行为：

- `baseURL = '/api'`
- 请求时使用后端 Session Cookie；非安全方法从 `TokenStore` 读取 CSRF token，并写入 `X-CSRF-Token`。
- `/login` 请求不强制带 CSRF token，`/login/logout` 需要带 CSRF token。
- 响应中 `code == 200` 或 `success === true` 视为成功。
- `401` 会跳转到 `/login`。

主要 API 文件：

- `LoginApi.ts`：登录。
- `HospitalManagementApi.ts`：医院管理。
- `DepartmentManagementApi.ts`：科室管理。
- `TitleManagementApi.ts`：职称管理。
- `DoctorManagementApi.ts`：医生管理。
- `KnowledgeDocumentApi.ts`：医生资料上传和资料列表。
- `RagDocumentApi.ts`：文档向量化、删除向量、向量检索。
- `KnowledgeVectorConversionApi.ts`：向量转换记录。
- `ArticleManagementApi.ts`：当前主要封装 `/chat` 生成接口，文章 CRUD 还需要继续整理。
- `AccountManagementApi.ts`：第三方账号列表、校验、删除。

## 8. 业务流程

### 8.1 医生知识库建设

1. 在医院、科室、职称页面维护基础字典。
2. 在医生管理页面新增医生。
3. 在知识库页面选择医生并上传资料。
4. 调用后端 `/document/upload` 保存资料。
5. 点击向量化，调用 `/rag/documents/convert`。
6. 后端解析文档并写入 Qdrant。
7. 向量化成功后，医生资料可用于文章生成。

### 8.2 文章生成

1. 在文章生成页面选择医生。
2. 输入生成需求或主题。
3. 调用 `/chat?userMessage=xxx&doctorId=xxx` 获取 SSE 流式结果。
4. 生成内容后续应保存到文章管理中。

当前文章生成还不是完整闭环，需要继续完善“生成参数、保存草稿、生成记录、二次编辑、平台适配”等能力。

### 8.3 第三方平台发布

1. 在账号管理页面选择平台并发起登录。
2. Java 后端代理调用 Python 发布服务 `/login`，Python 使用浏览器自动化完成登录和 Cookie 保存。
3. 账号保存到 Python 服务本地 SQLite。
4. 发布时选择文章、账号、平台、素材。
5. Java 后端调用 Python `/postArticle` 完成发布。

当前发布记录页面还没有真实闭环，需要后续把发布请求、状态、失败原因、第三方链接落库并展示。

## 9. 当前已知问题

- 部分源码中文注释存在编码异常，建议统一为 UTF-8。
- `ArticleManagementApi.ts` 目前只封装了 `/chat`，和文件名不完全匹配，后续应拆分为 `ChatApi` 和真正的 `ArticleManagementApi`。
- 路由 meta 中部分中文标题编码异常，但实际页面可用性需继续核对。
- `CoverManagement.vue`、`PublishRecord.vue`、`Profile.vue` 仍有较多静态示例数据。
- 账号登录页面中有硬编码 `http://localhost:5173/api`，部署时需要改为统一配置。
- Axios 超时时间为 10 秒，SSE、发布、上传等长耗时操作需要单独处理。
- 删除类接口已调整为 `DELETE`，状态切换和账号验证使用 `POST`。
- 前端登录与后端统一为 Session + CSRF 模型，`TokenStore` 仅保存当前标签会话的 CSRF token。
- 缺少环境变量配置文件，例如 `.env.development`、`.env.production`。
- 缺少端到端测试和核心页面的组件测试。

## 10. 后续建议开发清单

### 10.1 前端工程化

- 增加 `.env.development` 和 `.env.production`，统一配置 API 地址。
- 统一接口类型定义，补齐医生、文章、文档、账号、发布记录的 TypeScript 类型。
- 将 SSE 请求单独封装，避免被普通 Axios 超时策略影响。
- 整理 API 文件命名，拆分文章生成、文章管理、发布管理。

### 10.2 业务页面

- 完善文章生成页：生成参数、平台风格、字数、关键词、标题候选、保存草稿。
- 完善文章管理页：文章新增、编辑、删除、状态流转、预览。
- 完善封面管理页：上传、列表、删除、选择封面、和文章绑定。
- 完善发布记录页：发布中、成功、失败、失败原因、外部链接、重试。
- 完善知识库页面：批量上传、批量向量化、失败重试、进度展示。

### 10.3 体验和稳定性

- 上传、向量化、文章生成、发布都增加明确的 loading、禁用状态和错误提示。
- 对长文本生成结果增加保存前确认和防丢失提示。
- 账号登录 SSE 增加超时、取消、重试。
- 表格筛选条件和分页状态建议同步到 URL，方便刷新和分享。

### 10.4 权限和安全

- 等后端补齐用户体系后，前端增加角色菜单和按钮权限。
- 不在前端硬编码内部地址、测试图片或测试账号。
- 对外部链接、富文本内容做展示安全处理。

## 11. 交接人上手建议

建议先从以下页面开始联调：

1. `Login.vue`：确认认证模型。
2. `DoctorManagement.vue`：确认医生基础数据。
3. `KnowledgeBase.vue`：确认资料上传和向量化链路。
4. `ArticleGenerator.vue`：确认 SSE 生成链路。
5. `AccountManagement.vue`：确认 Python 发布服务登录链路。
6. `PublishRecord.vue` 和 `CoverManagement.vue`：优先补齐真实接口和数据闭环。
