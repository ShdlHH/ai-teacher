# AI 智能老师（ai-teacher）

基于 **Spring Boot 3 + Spring AI + Ollama** 的本地化 AI 教学平台。前端为原生 HTML 单页应用，后端负责对话、RAG 知识库、AI 出题与批改、错题本和学习统计，模型全部跑在本机 Ollama 上，无需调用云端 API。

## 功能特性

| 模块 | 能力 |
| --- | --- |
| 用户认证 | 注册 / 登录，BCrypt 密码加密，JWT 鉴权（默认 72 小时） |
| 流式对话 | SSE 流式输出，按会话保存历史，多轮上下文记忆 |
| RAG 知识库 | 上传 PDF / DOCX / MD / TXT，异步解析、切分、向量化，回答自动附带引用片段 |
| AI 出题 | 按学科、知识点、题型生成练习题（选择题 / 填空题 / 简答题），强制 JSON 输出 |
| 作答批改 | 选择题与填空题规则判分，简答题交给 AI 评分，返回标准答案与解析 |
| 错题本 | 答错自动收录，记录错误次数，支持标记已掌握与删除 |
| 学习统计 | 题目总数、作答次数、正确率、错题总数与掌握情况 |
| 前端 | 单页五页签：对话 / 练习 / 错题本 / 学习统计 / 知识库，列表支持分页加载更多 |

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 运行环境 | JDK 17、Maven 3.8+ |
| 框架 | Spring Boot 3.4.5、Spring Web、Validation |
| AI | Spring AI 1.0.0、Ollama、ChatClient、ChatMemory、SimpleVectorStore |
| 模型 | 对话 / 出题 `qwen3:8b`，向量化 `nomic-embed-text`（均可配置更换） |
| 数据层 | MySQL 8、MyBatis-Plus 3.5.10.1（含分页插件） |
| 鉴权 | jjwt 0.12.6、HandlerInterceptor、spring-security-crypto |
| 文档解析 | PDFBox 3.0.3、Apache POI 5.3.0 |

## 架构

```text
浏览器（static/index.html 单页五页签）
        │  JWT：Authorization: Bearer / SSE 使用 ?token=
        ▼
Controller 层（auth / conversations / chat / quiz / wrong-questions / knowledge / stats）
        ▼
Service 层（认证、会话记忆、RAG 检索、出题解析、规则+AI 判分、统计）
        ▼
Spring AI（ChatClient · MessageChatMemoryAdvisor · SimpleVectorStore · EmbeddingModel）
        ▼
MySQL 8（业务数据） + data/vector-store.json（向量库落盘）
```

## 目录结构

```text
ai-chat
├── pom.xml
└── src/main
    ├── java/com/example/aiteacher
    │   ├── common        # Result / ResultCode / BizException / JWT / UserContext
    │   ├── config        # ChatClient、向量库、Web、MyBatis-Plus 配置
    │   ├── controller    # 7 个 REST 控制器
    │   ├── dto           # 请求/响应对象（含 PageResult）
    │   ├── entity        # 数据库实体
    │   ├── mapper        # MyBatis-Plus Mapper
    │   └── service       # 业务逻辑（含 memory/DbChatMemory）
    └── resources
        ├── application.yml
        ├── db/init.sql   # 建库建表脚本
        ├── prompts/      # AI 老师 / 出题系统提示词
        └── static/index.html
```

## 快速开始

### 1. 环境准备

- JDK 17
- Maven 3.8+
- MySQL 8（默认 `localhost:3306`，账号 `root/123456`）
- [Ollama](https://ollama.com/) 已安装并运行在 `http://localhost:11434`

拉取所需模型：

```bash
ollama pull qwen3:8b
ollama pull nomic-embed-text
ollama serve   # 若服务未随安装自启
```

### 2. 初始化数据库

```bash
mysql -uroot -p < src/main/resources/db/init.sql
```

脚本会创建 `ai_teacher` 库及 9 张表（`user`、`conversation`、`chat_message`、`knowledge_document`、`knowledge_segment`、`question`、`question_attempt`、`wrong_question`、`study_record`）。

### 3. 修改配置

编辑 [application.yml](src/main/resources/application.yml)：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `spring.datasource.*` | MySQL 连接信息 | `root/123456` |
| `jwt.secret` | JWT 签名密钥（至少 32 字节） | 开发用密钥 |
| `jwt.expire-hours` | Token 有效期 | `72` |
| `spring.ai.ollama.chat.options.model` | 对话模型 | `qwen3:8b` |
| `spring.ai.ollama.chat.options.num-ctx` | 对话上下文窗口 | `8192` |
| `spring.ai.ollama.embedding.options.model` | 向量化模型 | `nomic-embed-text` |
| `ai.teacher.quiz.model` | 出题 / 批改模型 | `qwen3:8b` |
| `ai.teacher.quiz.temperature` | 出题温度 | `0.2` |
| `ai.teacher.history-limit` | 携带的历史消息条数 | `20` |
| `ai.teacher.rag.top-k` | 检索片段数 | `5` |
| `ai.teacher.rag.similarity-threshold` | 相似度阈值 | `0.5` |
| `ai.teacher.vector-store-path` | 向量库落盘路径 | `data/vector-store.json` |

> 出题客户端在 `ChatClientConfig` 中单独设置 `numCtx(4096)`，比对话上下文更小，可显著提升生成速度。

### 4. 启动

```bash
mvn spring-boot:run
```

或打包运行：

```bash
mvn -DskipTests package
java -jar target/ai-teacher-1.0.0.jar
```

启动后访问 <http://localhost:8080>，首次使用先注册账号再登录。

## 接口一览

所有接口统一返回 `{ code, message, data }`，列表接口的 `data` 为分页结构 `{ records, total, page, size }`。

### 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册，body：`username`、`password`、`nickname`(可选) |
| POST | `/api/auth/login` | 登录，返回 `token`、`username`、`nickname` |

### 会话与对话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/conversations` | 当前用户会话列表 |
| POST | `/api/conversations` | 新建会话，body：`subject`(可选) |
| DELETE | `/api/conversations/{id}` | 删除会话及其消息 |
| GET | `/api/conversations/{id}/messages` | 会话历史消息 |
| GET | `/api/chat/stream` | SSE 流式对话，参数：`conversationId`、`message` |

### 题库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/quiz/generate` | AI 出题，body：`subject`、`knowledgePoint`(可选)、`count`(1-5)、`type`(可选) |
| GET | `/api/quiz/questions` | 我的题目分页，参数：`subject`、`page`、`size`(默认 20) |
| GET | `/api/quiz/questions/{id}` | 题目详情 |
| POST | `/api/quiz/answer` | 提交作答，body：`questionId`、`userAnswer` |

### 错题本

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/wrong-questions` | 分页查询，参数：`subject`、`mastered`、`page`、`size`(默认 10) |
| PUT | `/api/wrong-questions/{id}/mastered` | 标记掌握，参数：`mastered`(默认 true) |
| DELETE | `/api/wrong-questions/{id}` | 删除错题 |

### 知识库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/knowledge/documents` | 上传文档（multipart `file`，支持 pdf/docx/md/txt） |
| GET | `/api/knowledge/documents` | 文档分页，参数：`page`、`size`(默认 10) |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档、片段与向量 |
| GET | `/api/knowledge/documents/{id}/segments` | 查看文档切分片段 |

### 统计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/stats/overview` | 题量、作答数、正确率、错题掌握情况 |

## SSE 事件

对话接口依次推送三类事件：

| 事件 | 数据 | 说明 |
| --- | --- | --- |
| `citations` | JSON 数组 | 命中的知识库引用片段，无命中则为空数组 |
| `token` | 文本增量 | 模型逐字输出 |
| `done` | 结束标记 | 流结束 |

示例（`EventSource` 无法自定义请求头，SSE 通过 `token` 查询参数鉴权）：

```bash
# 1. 登录拿 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"student01\",\"password\":\"123456\"}"

# 2. 新建会话，拿到 conversationId
curl -X POST http://localhost:8080/api/conversations \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"subject\":\"数学\"}"

# 3. 流式对话
curl -N "http://localhost:8080/api/chat/stream?token=<TOKEN>&conversationId=1&message=什么是勾股定理"
```

## RAG 处理流程

```text
上传文件 → 保存文档记录(PROCESSING) → @Async 异步任务
        → 解析文本(PDFBox / POI / 纯文本)
        → TokenTextSplitter 切分(chunkSize 400)
        → nomic-embed-text 向量化
        → 写入 SimpleVectorStore 并落盘 data/vector-store.json
        → 文档状态置为 READY（失败则清理片段并置为 FAILED）
```

对话时按 `user_id` 隔离检索，命中结果以 `citations` 事件返回，并拼接进提示词（上限 `max-context-chars`）。

## 关键设计

- **用户隔离**：所有业务表带 `user_id`，Service 层校验数据归属，越权返回 403。
- **统一返回体**：`Result<T>` 包装状态码与数据；分页统一 `PageResult<T>`，避免全量查询。
- **出题稳定性**：低温度 + `format("json")` + 去除 Markdown 代码块 + 字段校验，解析失败返回空列表而不抛异常。
- **判分策略**：选择题比对选项字母，填空题归一化后比较，简答题由 AI 返回 `correct/score/comment`。
- **上下文记忆**：`DbChatMemory` 按会话读取最近 `history-limit` 条消息，经 `MessageChatMemoryAdvisor` 注入对话。
- **异步入库**：知识库解析不阻塞 HTTP 请求，前端轮询文档状态。

## 已知限制

- `study_record` 表已建但尚未接入业务，学习记录功能为预留。
- 向量库使用 `SimpleVectorStore`（内存 + JSON 落盘），仅适合单机小规模数据，后续可替换为 PgVector / Milvus。
- Spring AI 1.0.0 的 `OllamaOptions` 暂不支持关闭 qwen3 思考模式，出题耗时会有波动。
- 8GB 显存设备建议使用 8B 级模型；14B 模型会溢出到 CPU 导致明显变慢。

## 安全提示

`application.yml` 中的数据库密码与 `jwt.secret` 均为本地开发配置，部署到公网前请改为环境变量或独立的本地配置文件，并更换 JWT 密钥。

## 说明

本项目用于学习与交流。
