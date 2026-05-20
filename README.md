# Flight Query Platform - 机票订单自然语言查询平台

## 项目简介

基于 LangChain4j 构建的自然语言驱动报表查询平台，运营人员通过自然语言提问，系统自动将问题转成 SQL 查询机票订单宽表，返回三层输出：

1. **自然语言结论** - 运营直接看这个
2. **结构化数据表格** - 数据分析师看这个  
3. **生成的SQL** - 开发/审计看这个

与现有 Metabase 形成互补：Metabase 负责固定指标监控，本平台负责临时探索性查询。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 11 | 运行环境 |
| SpringBoot | 2.7.18 | 基础框架 |
| LangChain4j | 0.35.0 | AI工程化框架 |
| 通义千问 (qwen-plus) | - | SQL生成 + 结果解释 |
| bge-small-zh (ONNX) | - | 本地向量化模型（中文优化）|
| MyBatis-Plus | 3.5.5 | ORM |
| Redis | - | 对话历史 + 实体存储 + 缓存 |
| MySQL | 8.0 | 数据存储 |

## 项目结构

```
flight-query-platform/
├── flight-query-common/     # 公共模块（Result、ErrorCode、常量、工具类）
├── flight-query-domain/     # 领域模块（Entity、Mapper、建表SQL）
├── flight-query-service/    # 核心服务模块
│   ├── schema/              # Schema语义匹配（向量化+字段组管理）
│   ├── context/             # 上下文管理（两层存储：历史+实体）
│   ├── sql/                 # SQL生成、安全校验、自动修正
│   └── query/               # 主流程编排
├── flight-query-api/        # 接口模块（Controller、DTO、异常处理）
└── flight-query-start/      # 启动模块（配置、Bean注册）
```

## 快速启动

### 1. 环境准备

- JDK 11+
- MySQL 8.0+
- Redis
- 通义千问 API Key（[获取地址](https://dashscope.console.aliyun.com/)）

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
spring.redis.host: your-redis-host

# 填入通义千问API Key
langchain4j.dashscope.api-key: sk-your-real-api-key
```

### 4. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar flight-query-start/target/flight-query-start-1.0.0-SNAPSHOT.jar
```

### 5. 测试接口

```bash
# 健康检查
curl http://localhost:8080/api/query/health

# 发起查询
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "user001_abc123",
    "question": "上周欧洲航线按航司的利润排名"
  }'

# 追问（同一个sessionId）
curl -X POST http://localhost:8080/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "user001_abc123",
    "question": "那这个航司上个月的趋势呢"
  }'

# 清除会话
curl -X DELETE http://localhost:8080/api/query/session/user001_abc123
```

## 核心设计

### 动态Schema注入
100+字段按业务维度分为8个组，通过bge-small-zh向量化后存入InMemoryEmbeddingStore，
用户问题实时向量化匹配最相关的2~3个组注入Prompt，Token消耗降低约40%。

### 多轮对话（两层存储）
- 第一层：Redis List存对话历史（最近10轮）
- 第二层：每轮提取业务实体（航司/时间/地区）存Redis，下轮注入Prompt

### SQL安全防护（三层）
1. System Prompt约束模型只生成SELECT
2. 代码层黑名单关键词拦截
3. 数据库只读账号兜底

### SQL自动修正
执行失败时将报错信息+历史SQL回传模型修正，最多重试3次。

## API文档

### POST /api/query - 自然语言查询

**请求：**
```json
{
  "sessionId": "user001_abc123",
  "question": "上周欧洲航线按航司的利润排名"
}
```

**成功响应：**
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "conclusion": "上周欧洲航线总利润$45,230，汉莎航空以$12,400排名第一...",
    "data": [
      {"airline": "LH", "profit": 12400.00},
      {"airline": "BA", "profit": 9800.00}
    ],
    "sql": "SELECT outbound_marketing_airline AS airline, SUM(flight_profit_usd) AS profit FROM report_reservation_real_time WHERE ...",
    "rowCount": 5
  },
  "timestamp": 1719216000000
}
```

### DELETE /api/query/session/{sessionId} - 清除会话

### GET /api/query/health - 健康检查
