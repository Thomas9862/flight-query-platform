# Flight Query Platform - 国际机票智能分析Agent

## 项目简介

基于 **LangChain4j AI Services** 构建的多工具 Agent 智能体，通过 **Function Calling** 让大模型自主选择工具链完成国际机票数据分析任务。

与传统固定管道（Chain）不同，本项目采用 **Agent 模式**：LLM 作为决策者，自主决定调用哪些工具、以什么顺序执行、遇到错误是否重试，真正实现"让模型自己思考"。

### Agent vs Chain

| 对比维度 | 传统Chain模式 | 本项目Agent模式 |
|---------|-------------|---------------|
| 决策者 | 代码固定编排 | LLM 自主推理 |
| 工具调用 | 代码直接调JDBC | LLM 通过 Function Calling 选择 Tool |
| 错误处理 | while循环重试 | LLM 看到错误后自主修正 |
| 扩展性 | 改流程代码 | 新增 @Tool 即可 |

## 核心架构

```
用户问题
   │
   ▼
┌─────────────────────────────────┐
│     FlightQueryAgent (@AiService)│
│     LLM 自主决策调用以下工具：      │
│                                   │
│  ┌──────────┐  ┌──────────────┐  │
│  │ Schema   │  │ SQL          │  │
│  │ Lookup   │  │ Execution    │  │
│  │ Tool     │  │ Tool         │  │
│  └──────────┘  └──────────────┘  │
│  ┌──────────┐  ┌──────────────┐  │
│  │ DateTime │  │ Airline      │  │
│  │ Tool     │  │ Knowledge    │  │
│  │          │  │ Tool         │  │
│  └──────────┘  └──────────────┘  │
│  ┌──────────────────────────────┐│
│  │ Business Knowledge Tool     ││
│  │ (RAG 向量知识库检索)          ││
│  └──────────────────────────────┘│
└─────────────────────────────────┘
   │
   ▼
分析结论（支持SSE流式输出）
```

## 5个Agent工具

| 工具 | 说明 | 技术实现 |
|------|------|---------|
| **SchemaLookupTool** | 根据问题语义匹配相关表字段 | MySQL动态管理字段组 + bge-small-zh向量匹配，支持CRUD扩展 |
| **SqlExecutionTool** | 执行SQL并返回结果，错误以文本返回让LLM自主修正 | JdbcTemplate + SqlSafetyChecker 三层安全防护 |
| **DateTimeTool** | 返回当前日期和常用时间范围 | 解决LLM不知"今天"的问题 |
| **AirlineKnowledgeTool** | IATA代码与航司名称双向映射 | 覆盖全球40+主要航司 |
| **BusinessKnowledgeTool** | RAG业务知识库检索 | MySQL存储 + Elasticsearch向量检索，支持CRUD管理 |

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.5 | 基础框架 |
| LangChain4j | 0.36.0 | AI Services + Tool Use + ChatMemory |
| DeepSeek (deepseek-chat) | - | Agent推理 + Function Calling |
| bge-small-zh (ONNX) | - | 本地向量化模型（中文优化）|
| MyBatis-Plus | 3.5.5 | ORM |
| Elasticsearch | 8.x | RAG知识库向量存储与检索 |
| Redis | - | 语义缓存 + 防重校验 |
| MySQL | 8.0 | 数据存储 + 知识库存储 |
| WebFlux | - | SSE 流式响应 |

## 项目结构

```
flight-query-platform/
├── flight-query-common/        # 公共模块（Result、ErrorCode、常量、工具类）
├── flight-query-domain/        # 领域模块（Entity、Mapper、建表SQL）
├── flight-query-service/       # 核心服务模块
│   ├── agent/
│   │   ├── FlightQueryAgent    # @AiService Agent接口（核心）
│   │   ├── AgentQueryService   # 薄编排层（缓存、防重、审计）
│   │   └── tool/               # 5个@Tool工具类
│   │       ├── SchemaLookupTool
│   │       ├── SqlExecutionTool
│   │       ├── DateTimeTool
│   │       ├── AirlineKnowledgeTool
│   │       └── BusinessKnowledgeTool (RAG)
│   ├── knowledge/              # 知识库管理（MySQL CRUD + ES向量同步）
│   ├── schema/                 # Schema语义匹配（8个字段组、向量存储）
│   └── sql/                    # SQL安全校验器
├── flight-query-api/           # 接口模块（Controller、DTO、异常处理）
│   └── controller/
│       ├── QueryController     # Agent查询接口（阻塞+SSE流式）
│       └── KnowledgeController # 知识库CRUD管理接口
├── flight-query-start/         # 启动模块（配置、Bean注册）
└── resources/sql/init.sql      # 建表SQL + 知识库初始数据
```

## 快速启动

### 1. 环境准备

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis
- Elasticsearch 8.x（知识库向量检索）
- DeepSeek API Key（[获取地址](https://platform.deepseek.com/)）

### 2. 初始化数据库

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS flight_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;

-- 创建只读账号（SQL安全防护兜底）
CREATE USER 'flight_readonly'@'%' IDENTIFIED BY 'your_password_here';
GRANT SELECT ON flight_db.* TO 'flight_readonly'@'%';
FLUSH PRIVILEGES;

-- 执行建表SQL
-- 文件路径：flight-query-domain/src/main/resources/sql/init.sql
```

### 3. 修改配置

编辑 `flight-query-start/src/main/resources/application.yml`：

```yaml
# 修改数据库连接
spring.datasource.url: jdbc:mysql://your-host:3306/flight_db
spring.datasource.password: your_password_here

# 修改Redis连接
spring.data.redis.host: your-redis-host

# 修改Elasticsearch连接
elasticsearch.server-url: http://your-es-host:9200

# 填入DeepSeek API Key（https://platform.deepseek.com/）
langchain4j.deepseek.api-key: sk-your-deepseek-api-key
```

### 4. 编译运行

```bash
mvn clean package -DskipTests
java -jar flight-query-start/target/flight-query-start-1.0.0-SNAPSHOT.jar
```

### 5. 测试接口

```bash
# 健康检查
curl http://localhost:8080/api/query/health

# 普通查询
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "user001_abc123", "question": "上周欧洲航线按航司的利润排名"}'

# SSE流式查询（实时看到Agent调用工具的过程）
curl -N -X POST http://localhost:8080/api/query/stream \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "user001_abc123", "question": "Emirates上个月的订单量和利润趋势"}'

# 多轮对话（同一个sessionId，Agent自动保持上下文）
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "user001_abc123", "question": "那它的增值产品利润呢"}'
```

## 核心设计

### Agent自主决策

通过 LangChain4j `@AiService` + `@Tool` 注解实现 Function Calling。LLM 接收用户问题后自主推理：

```
用户: "Emirates上个月的利润"
Agent思考: 需要知道当前日期 → 调用 DateTimeTool
Agent思考: 需要Emirates的IATA代码 → 调用 AirlineKnowledgeTool → 得到 "EK"
Agent思考: 需要知道利润相关字段 → 调用 SchemaLookupTool
Agent思考: 构造SQL → 调用 SqlExecutionTool
Agent思考: 得到数据，给出分析结论
```

### 动态Schema注入（MySQL管理）

100+字段按业务维度分为8个组（时间、订单、航线、利润、增值产品、支付、乘客、供应商），存储在 MySQL `schema_field_group` 表中，启动时加载并通过 bge-small-zh 向量化。Agent 调用 SchemaLookupTool 时自动匹配最相关的字段组注入 Prompt，避免全量字段塞入上下文。字段组支持 CRUD 管理，新增字段或新表无需改代码，`table_name` 列预留多表扩展能力。

### RAG业务知识库（MySQL + Elasticsearch）

知识源存储在 MySQL `knowledge_base` 表中（支持 CRUD 管理），启动时自动通过 bge-small-zh 向量化写入 Elasticsearch。Agent 通过 BusinessKnowledgeTool 在 ES 中做余弦相似度检索，获取最相关的知识片段辅助分析。

知识分类：业务指标定义（BUSINESS_GLOSSARY）、退改签规则（FARE_RULES）、航司知识（AIRLINE_KNOWLEDGE）、查询分析指南（QUERY_GUIDE），共 26 条初始知识。

### SQL安全防护（三层）

1. **System Prompt 约束** - 模型层面约束只生成 SELECT
2. **SqlSafetyChecker 代码校验** - 黑名单关键词 + 注入检测 + 多语句拦截
3. **数据库只读账号** - 最终兜底

### SSE流式输出

通过 `StreamingChatLanguageModel` + WebFlux `Flux<ServerSentEvent>` 实现实时流式响应，用户可以看到 Agent 逐步输出分析结论的过程。

### ChatMemory多轮对话

使用 LangChain4j 内置的 `MessageWindowChatMemory`（滑动窗口20条消息），按 sessionId 隔离，自动维护对话上下文，无需手动管理历史。

## API文档

### POST /api/query - Agent查询（阻塞）

```json
// 请求
{"sessionId": "user001_abc123", "question": "上周各航司利润排名"}

// 响应
{
  "code": 200,
  "message": "成功",
  "data": "上周各航司利润排名如下：汉莎航空(LH)以$12,400排名第一...",
  "timestamp": 1719216000000
}
```

### POST /api/query/stream - Agent查询（SSE流式）

```
// 请求: 同上
// 响应: Server-Sent Events 流
data: 上周
data: 各航司
data: 利润排名
data: 如下：
data: ...
event: done
data: [DONE]
```

### GET /api/query/health - 健康检查

### 知识库管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/knowledge | 查询所有启用的知识条目 |
| GET | /api/knowledge/all | 查询所有知识条目（含禁用） |
| GET | /api/knowledge/category/{category} | 按分类查询 |
| GET | /api/knowledge/{id} | 根据ID查询 |
| POST | /api/knowledge | 新增知识条目（自动同步ES） |
| PUT | /api/knowledge/{id} | 更新知识条目（自动重建ES索引） |
| DELETE | /api/knowledge/{id} | 删除知识条目（自动重建ES索引） |
| POST | /api/knowledge/rebuild-index | 手动触发全量重建ES索引 |

### Schema字段组管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/schema | 查询所有启用的字段组 |
| GET | /api/schema/all | 查询所有字段组（含禁用） |
| GET | /api/schema/{id} | 根据ID查询 |
| POST | /api/schema | 新增字段组（自动重载向量） |
| PUT | /api/schema/{id} | 更新字段组（自动重载向量） |
| DELETE | /api/schema/{id} | 删除字段组（自动重载向量） |
| POST | /api/schema/reload | 手动触发字段组向量重载 |
