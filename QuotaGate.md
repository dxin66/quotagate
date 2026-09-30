# 项目二设计文档：QuotaGate

## Multi-Tenant LLM API Gateway

# 1. 项目定位

### 一句话介绍

> QuotaGate 是一个面向多 Agent / 多业务的 OpenAI-compatible LLM API Gateway，统一提供 API Key 鉴权、模型路由、Redis 多级缓存、Token 配额原子预占、异步 Usage Ledger 和 Provider 容灾。

这不是：

> “Java 调一下 OpenAI API”。

而是：

> **解决公司多个 Agent 服务共享大模型 API 时的流量、额度、缓存和可靠性问题。**

---

# 2. 项目背景

公司可能同时有：

```text
Customer Agent
Coding Agent
RAG Agent
Data Agent
```

如果每个系统自己直接调：

```text
OpenAI
Claude
Gemini
```

会产生：

```text
API Key 分散

Provider 配置重复

限流不统一

无法控制 Token 配额

费用难统计

故障无法 fallback

缓存重复建设
```

因此统一：

```text
Agents
  │
  ▼
QuotaGate
  │
  ├── OpenAI
  ├── Claude
  └── Mock / Local
```

---

# 3. 第一周项目目标

完成核心链路：

```text
Request
  ↓
API Key Auth
  ↓
Tenant
  ↓
Cache
  ↓
Token Quota Reserve
  ↓
Provider
  ↓
Token Settlement
  ↓
Usage Event
  ↓
Redis Stream
  ↓
MySQL Ledger
```

并完成：

```text
k6 Benchmark
Prometheus metrics
```

---

# 4. 非目标

一周不要做：

```text
Semantic Cache
Vector DB
Prompt Management
复杂 RBAC
Kubernetes
Kafka
Billing UI
完整 PII Gateway
十几个 Provider
```

尤其不要同时上：

```text
Kafka + Redis Stream
```

选 Redis Stream 就够。

---

# 5. 技术亮点

---

# 5.1 OpenAI-compatible Gateway

对外暴露：

```http
POST /v1/chat/completions
GET /v1/models
```

Request：

```json
{
  "model": "gpt-standard",
  "messages": [
    {
      "role": "user",
      "content": "hello"
    }
  ],
  "stream": false
}
```

Provider abstraction：

```java
public interface LlmProvider {

    ChatResponse chat(ChatRequest request);

    Flux<ChatChunk> stream(ChatRequest request);

}
```

实现：

```text
MockProvider
OpenAIProvider
```

第一周两个够了。

---

# 5.2 Caffeine + Redis + MySQL

配置类数据：

```text
API Key
Tenant
Model Route
```

读取：

```text
Caffeine
   ↓ miss
Redis
   ↓ miss
MySQL
```

### 缓存穿透

非法 API Key：

```text
Negative Cache
```

### 缓存击穿

热门：

```text
model:gpt-standard
```

采用：

```text
Logical Expiration
+
Single Flight
+
Async Refresh
```

### 缓存雪崩

```text
TTL + random jitter
```

---

# 5.3 Redis Lua Token Quota

这是整个项目核心。

普通 API：

```text
限制 requests / second
```

LLM：

```text
request A = 100 token
request B = 10000 token
```

因此需要：

```text
Token Quota
```

流程：

```text
estimate
  ↓
reserve
  ↓
call provider
  ↓
actual usage
  ↓
settle
```

---

## Redis 数据

```text
qg:quota:{tenantId}:{month}
```

Hash：

```text
limit
used
reserved
```

---

## Reserve

原子执行：

```lua
if used + reserved + requested > limit then
    return 0
end

reserved = reserved + requested

return 1
```

---

## Settlement

假设：

```text
reserved = 4000

actual = 2600
```

执行：

```text
reserved -= 4000

used += 2600
```

Provider 失败：

```text
reserved -= 4000
```

这解决：

```text
check → update
```

之间的 race condition。

---

# 5.4 Redis Stream Usage Ledger

LLM 调用结束：

```text
Usage Event
```

例如：

```json
{
  "requestId": "abc123",
  "tenantId": 7,
  "provider": "openai",
  "inputTokens": 1200,
  "outputTokens": 800
}
```

进入：

```text
Redis Stream
```

Consumer：

```text
usage-consumer
```

写：

```text
usage_ledger
```

---

## 幂等

MySQL：

```sql
UNIQUE(request_id, event_type)
```

因此即使：

```text
DB INSERT success

ACK failed
```

第二次消费：

```text
duplicate key
```

不会重复计费。

---

# 5.5 Provider Resilience

采用：

```text
Resilience4j
```

顺序：

```text
Timeout
 ↓
Retry
 ↓
Circuit Breaker
 ↓
Fallback
```

不要自己实现 Circuit Breaker。

第一周只实现：

```text
MockFastProvider
MockSlowProvider
MockFailureProvider
```

这样 benchmark 可复现。

---

# 6. 技术栈

推荐：

```text
Java 21

Spring Boot 3
Spring MVC
WebClient

MyBatis-Plus / MyBatis
MySQL

Caffeine
Redis
Redis Lua
Redis Stream

Resilience4j

Micrometer
Prometheus

k6

Docker Compose
Testcontainers
JUnit 5
```

不建议 WebFlux 做整个 Gateway。

Streaming 需要的地方用：

```text
WebClient + SSE
```

---

# 7. 系统架构

```text
                   Client
                     │
                     ▼
            OpenAI Compatible API
                     │
                API Key Auth
                     │
                     ▼
               Tenant Context
                     │
                     ▼
               Token Quota
                     │
                     ▼
               Model Router
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
     OpenAI      Mock Slow    Mock Fail
        │
        └────────────┬────────────┘
                     ▼
               Usage Settle
                     │
                     ▼
               Redis Stream
                     │
                     ▼
                Usage Ledger
```

缓存旁路：

```text
Caffeine
  ↓
Redis
  ↓
MySQL
```

---

# 8. 数据库设计

---

## tenant

```sql
CREATE TABLE tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    name VARCHAR(128) NOT NULL,

    status VARCHAR(32) NOT NULL,

    created_at DATETIME NOT NULL
);
```

---

## api_key

```text
id

tenant_id

key_prefix

key_hash

status

expires_at

created_at
```

不存明文。

生成：

```text
qg_sk_xxxxx
```

只展示一次。

DB 只存：

```text
prefix

SHA-256 hash
```

---

## model_route

```text
id

model_alias

provider

upstream_model

priority

enabled

input_price

output_price
```

例如：

```text
gpt-standard
     ↓
openai / gpt-x
```

---

## request_record

```text
id

request_id

tenant_id

model_alias

provider

status

input_tokens
output_tokens

latency_ms
ttft_ms

created_at
```

`request_id` UNIQUE。

---

## usage_ledger

```text
id

request_id

tenant_id

event_type

reserved_tokens

actual_tokens

delta_tokens

created_at
```

---

# 9. Redis Key

统一 prefix：

```text
qg:
```

---

## API Key

```text
qg:apikey:{hash}
```

---

## Tenant

```text
qg:tenant:{tenantId}
```

---

## Model

```text
qg:model:{alias}
```

---

## Token Quota

```text
qg:quota:{tenantId}:{yyyyMM}
```

---

## Stream

```text
qg:stream:usage
```

---

# 10. 缓存设计

## API Key

流程：

```text
Caffeine
  ↓
Redis
  ↓
MySQL
```

invalid key：

```text
qg:apikey:null:{hash}
```

TTL：

```text
30~60 seconds
```

避免随机 Key 攻击不断 hit DB。

---

# 11. Logical Expiration

Model config：

```json
{
  "data": {
    "provider": "openai",
    "model": "gpt-x"
  },
  "expireAt": 1750000123
}
```

如果逻辑过期：

```text
Thread A
  ↓
try lock
  ↓
refresh cache

Thread B/C/D
  ↓
return stale config
```

这样避免 hot key 同时打 DB。

---

# 12. Provider Mock

为了 benchmark，一定写。

---

## FastProvider

```text
latency = 50 ms

failure = 0%
```

---

## SlowProvider

```text
latency = 2000 ms
```

---

## FailProvider

```text
failure rate = 80%
```

配置：

```yaml
mock:
  latency-ms: 100

  failure-rate: 0.1
```

---

# 13. Metrics

所有指标采用生产常用指标。

Gateway：

```text
RPS

Error Rate

P50
P95
P99
```

Redis：

```text
Cache Hit Rate

Cache Miss

evicted_keys
```

LLM：

```text
TTFT

input tokens

output tokens

provider latency
```

不要发明：

```text
Gateway Score
Cache Stability Score
```

---

# 14. Benchmark 场景

最终只做 4 组。

---

## Benchmark 1

Normal Load：

```text
50 users
100 users
300 users
```

报告：

```text
RPS
P95
P99
Error Rate
```

---

## Benchmark 2

Hot-Key Expiration：

```text
500 concurrent users
```

比较：

```text
Cache Aside
```

vs

```text
Logical Expiration + Single Flight
```

记录：

```text
DB query rate

P95 latency

Cache Hit Rate
```

---

## Benchmark 3

Quota Concurrency

例如：

```text
quota = 100000 tokens
```

1000 concurrent requests。

检查 invariant：

```text
used + reserved <= limit
```

这是 correctness test，不叫指标。

---

## Benchmark 4

Provider Failure

```text
80% provider failure
```

观察：

```text
Error Rate
P95
fallback count
```

---

# 15. 项目结构

```text
quotagate/
│
├── src/main/java/com/quotagate/
│
│   ├── api/
│   │   ├── ChatController.java
│   │   └── dto/
│   │
│   ├── auth/
│   │   ├── ApiKeyFilter.java
│   │   └── ApiKeyService.java
│   │
│   ├── tenant/
│   │
│   ├── cache/
│   │   ├── CacheService.java
│   │   ├── LogicalExpireCache.java
│   │   └── CacheKey.java
│   │
│   ├── quota/
│   │   ├── QuotaService.java
│   │   ├── Reservation.java
│   │   └── lua/
│   │       ├── reserve.lua
│   │       └── settle.lua
│   │
│   ├── provider/
│   │   ├── LlmProvider.java
│   │   ├── MockProvider.java
│   │   └── OpenAiProvider.java
│   │
│   ├── routing/
│   │   └── ProviderRouter.java
│   │
│   ├── usage/
│   │   ├── UsageEvent.java
│   │   ├── UsageProducer.java
│   │   └── UsageConsumer.java
│   │
│   ├── ledger/
│   │
│   ├── resilience/
│   │
│   ├── observability/
│   │
│   └── persistence/
│
├── src/test/
│
├── benchmark/
│   ├── load.js
│   ├── hot-key.js
│   └── quota.js
│
└── docker-compose.yml
```

---

# 16. 每日开发计划

## Day 1：Gateway 主链路

完成：

```text
Spring Boot

/v1/chat/completions

MockProvider

OpenAI-compatible DTO

API Key
```

先不接 Redis。

Request：

```text
Client → Gateway → MockProvider
```

完成 request_id。

### 验收

curl：

```bash
POST /v1/chat/completions
```

返回 OpenAI 风格 JSON。

---

# Day 2：MySQL + Cache

建立：

```text
tenant

api_key

model_route
```

实现：

```text
Caffeine
↓
Redis
↓
MySQL
```

统计：

```text
cache hit
cache miss
DB query count
```

当天完成：

```text
negative cache
TTL jitter
```

---

# Day 3：缓存击穿

实现：

```text
Logical Expire

Single Flight

Async Refresh
```

重点写测试：

```text
500 concurrent
same model key
```

比较：

```text
baseline

optimized
```

跑第一次 k6。

---

# Day 4：Redis Lua Token Quota

实现：

```text
reserve.lua
settle.lua
refund
```

Java：

```java
QuotaReservation reserve(
    long tenantId,
    long estimatedTokens
);
```

调用：

```text
reserve
↓
provider
↓
settle
```

Provider error：

```text
refund
```

### 当天必须写并发 test

```text
1000 threads

quota fixed
```

Assert：

```text
used + reserved <= limit
```

---

# Day 5：Redis Stream Ledger

实现：

```text
XADD

XREADGROUP

XACK
```

Consumer Group：

```text
billing-consumer
```

MySQL：

```text
UNIQUE(request_id,event_type)
```

测试：

```text
insert succeeds

ACK fails

message reconsumed
```

最终：

```text
DB 只有一条
```

---

# Day 6：Resilience + Observability

加入：

```text
timeout

retry

circuit breaker

fallback
```

接：

```text
Micrometer

Prometheus
```

Metrics：

```text
gateway_request_total

gateway_request_duration

gateway_errors_total

cache_hits

cache_misses

provider_latency

input_tokens

output_tokens
```

---

# Day 7：Benchmark

完整跑：

### Load

```text
50
100
300 concurrency
```

### Hot key

```text
baseline
vs
optimized
```

### Provider Failure

```text
normal
vs
80% fail
```

### Quota Concurrency

```text
1000 concurrent
```

输出：

```text
benchmark-results.md
```

README：

```text
Problem

Architecture

Cache

Quota

Ledger

Resilience

Benchmarks
```

---

# 17. GitHub 参考

---

## 参考 1：Mani9333/llm-gateway

这是我最推荐你参考的 Java 项目。

它本身定位就是小型、production-shaped 的 OpenAI-compatible LLM Gateway，包含：

- Spring Boot；
    
- provider abstraction；
    
- routing；
    
- SSE；
    
- rate limiting；
    
- token / cost accounting；
    
- Micrometer / Prometheus；
    
- mock provider。
    

而且作者明确把 Redis-backed cache/limiter、multi-tenant 等列为扩展方向，非常适合你在它的思路之上自己实现更深的 Redis 工程机制。([GitHub](https://github.com/Mani9333/llm-gateway?utm_source=chatgpt.com "GitHub - Mani9333/llm-gateway: An OpenAI-compatible LLM gateway in Spring Boot: multi-provider routing + fallback, caching, rate-limiting, token/cost accounting, SSE streaming, and Prometheus metrics. Runs locally with zero keys. · GitHub"))

[Mani9333/llm-gateway](https://github.com/Mani9333/llm-gateway?utm_source=chatgpt.com)

主要看：

```text
provider/
routing/
ratelimit/
cost/
observability/
```

---

## 参考 2：Aryaman9/llm-gateway

这个项目和我们的设计非常接近：

- Java 21；
    
- Spring Boot；
    
- Redis；
    
- token-based limiting；
    
- Redis + Lua；
    
- Circuit Breaker；
    
- Kafka async audit/cost；
    
- multi-provider fallback。
    

它的目录结构也非常值得参考。([GitHub](https://github.com/Aryaman9/llm-gateway?utm_source=chatgpt.com "GitHub - Aryaman9/llm-gateway: Production-grade LLM Gateway with token-based rate limiting, semantic caching, and multi-provider circuit breaker fallback · GitHub"))

[Aryaman9/llm-gateway](https://github.com/Aryaman9/llm-gateway?utm_source=chatgpt.com)

但是注意：

> **不要照着它把 semantic cache、PII、Kafka 全部抄进来。**

你只研究：

```text
ratelimit
router
provider
telemetry
```

然后自己采用：

```text
Redis Stream
```

替换 Kafka。

---

## 参考 3：vahid8/llm-gateway

这个项目很适合理解：

```text
per-key rate limit
budget
gateway-issued API key
multi-provider
usage accounting
```

尤其它自己说明了“内存限流在 multi-worker 下不严格，需要 Redis 才能实现严格全局限制”，以及 budget 预检查可能被最后一个请求超出的问题，这正好对应我们为什么要设计 Redis 原子 quota reservation。([GitHub](https://github.com/vahid8/llm-gateway?utm_source=chatgpt.com "GitHub - vahid8/llm-gateway: Open-source, OpenAI-compatible LLM gateway over OpenAI, Anthropic & Gemini — one endpoint, cost/latency/token tracking, gateway-issued keys, streaming, retries + fallback. FastAPI + LiteLLM. · GitHub"))

[vahid8/llm-gateway](https://github.com/vahid8/llm-gateway?utm_source=chatgpt.com)

---

# 18. 两个项目开发时的参考策略

不要这样：

```text
git clone xxx

修改名字

删除几个模块

提交
```

建议：

### 第一步

先看 README：

```text
解决什么问题

架构怎么拆
```

### 第二步

只看关键 interface。

例如 QuotaGate：

```text
LlmProvider

ProviderRouter
```

Agent：

```text
AgentState

Tool Interface
```

### 第三步

关掉 GitHub。

自己手敲。

遇到设计问题再回来查。

这样面试时你才能真正讲清楚。

---