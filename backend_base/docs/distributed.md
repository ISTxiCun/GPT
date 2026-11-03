# 分布式基础能力 V1

本版提供单体多实例下的锁、限流和短窗口防重复提交。业务模块按需调用，无需增加 Maven 子模块或独立的“公共服务”。不会生成用户、订单、商品等业务模型。

## 1. 开启功能

默认全部关闭，现有启动流程不变。环境变量也可按 Spring Boot 松散绑定规则开启：

```bash
export APP_ENV=local
export APP_DISTRIBUTED_LOCK_ENABLED=true
export APP_DISTRIBUTED_RATELIMIT_ENABLED=true
export APP_DISTRIBUTED_SUBMISSION_ENABLED=true
```

或者在自己的环境配置文件添加：

```yaml
app:
  distributed:
    namespace: ${spring.application.name}
    environment: ${APP_ENV:}
    lock:
      enabled: true
      watchdog-millis: 30000
    rate-limit:
      enabled: true
    submission:
      enabled: true
    trusted-proxies: []
```

启用任一能力后，`environment` 必须明确指定；namespace 与 environment 均限制为 1～64 位字母、数字、下划线、连字符。例如 `backend-base:local`、`backend-base:prod`。同一个部署的所有实例必须一致，不同项目/环境必须隔离。

Redis Key 格式为 `namespace:environment:dist:能力:SHA256摘要`。摘要由长度编码后的业务 Key 各部分生成，避免分隔符歧义，且不把用户信息原样放进 Key；摘要不等于加密。

关闭的功能不会创建对应执行组件，也不会创建 Redisson 连接。接口使用注解却没开启对应配置时，应用启动失败；注解参数非法、路径不在 `/api/` 下或防重注解用在读接口上也会启动失败。锁注入不存在时同样会启动报错，不会静默绕过保护。

当前锁适配器支持 **standalone Redis**，复用 `spring.data.redis` 的 host、port、username、password、database、连接超时与 SSL 开关。URL、Sentinel、Cluster 配置暂不支持，开启锁时会明确拒绝；不要把未支持的拓扑当成已经提供了高可用保证。自定义 TLS 信任材料和其他拓扑需补充适配。

限流和防重复用现有 `StringRedisTemplate`。这些能力的 Key 环境隔离不改变 Sa-Token 默认 Key 命名；登录会话也应使用独立 Redis 实例/数据库或专门的 Sa-Token 命名配置隔离环境。

## 2. 分布式锁

业务 Service 通过构造器注入 `LockExecutor`，同步调用：

```java
return lockExecutor.execute(
    "resource:update:" + resourceId,
    Duration.ofSeconds(2),
    () -> transactionalService.update(command)
);
```

`transactionalService` 是另一个 Spring Bean，其公开方法按需标注 `@Transactional`；方法返回前事务完成提交，之后执行器才释放锁。

- 必须在事务外获取锁。检测到当前线程已有活动事务时直接拒绝执行，避免外层事务晚于锁释放才提交。
- 不要在锁内通过同类自调用“启动事务”；那样可能没有经过 Spring 事务代理。
- 等待时间允许 0～30 秒，0 表示立即尝试；锁 Key 为 1～512 字符。
- 默认不设置固定租约，使用 Redisson 看门狗续期；`watchdog-millis` 默认 30000，允许 1000～300000。
- 获取失败返回 HTTP 409 / `LOCK_BUSY`。等待中断会恢复线程中断标记，返回 503 / `LOCK_INTERRUPTED`。
- 获取锁时 Redis 故障返回 503 / `DISTRIBUTED_UNAVAILABLE`。
- 回调抛出异常仍会释放锁；释放操作由 Redisson 校验持锁线程，不使用 `forceUnlock`。
- 释放失败且回调已成功时返回 503 / `LOCK_RELEASE_FAILED`，业务结果可能已落库；若回调本身失败，保留原异常，将释放异常附加为 suppressed。

执行器只保护**当前线程内同步完成的回调**。不要返回 Future、启动异步任务后立即返回，或在另一个线程继续关键操作。不自动重试业务，不跨线程传递锁。

看门狗不是永久互斥证明。网络分区、Redis 故障转移、长时间进程暂停可能导致锁丢失。库存、支付等关键写入仍需数据库条件更新、唯一约束或适合业务的 fencing/version 机制。V1 不提供 fencing token。

## 3. 接口限流

在 MVC Controller 方法上使用：

```java
@RateLimit(
    key = "article:create",
    dimension = RateLimit.Dimension.USER,
    limit = 10,
    windowSeconds = 60
)
@PostMapping("/api/articles")
public ApiResponse<?> create(...) { ... }
```

注解只作用于映射到 `/api/**` 的同步 MVC Controller 方法，不是任意 Service 方法的 AOP 注解。V1 不支持 Callable、CompletionStage、DeferredResult、WebAsyncTask 或 SSE/流式异步返回值，使用时启动会明确报错；需要异步接口时应先补齐认证上下文与重复分发处理。

| 维度 | 实际计数主体 |
|---|---|
| `USER`，默认 | Sa-Token 当前登录用户；即使位于 public 路径，未登录也返回 401 |
| `IP` | 实际客户端地址，按下面的可信代理规则解析 |
| `GLOBAL` | 所有请求共享一个额度 |

同一个 namespace、environment、key、dimension、subject 共享一个额度。多个接口若使用相同 key 会共享计数；要独立限流请使用不同 key。相同 key 的 limit 和 window 必须在所有调用方保持一致。

限流使用单 Key 的 Redis Lua 脚本，计数和设置 TTL 在一次原子操作内完成。窗口从第一次通过的请求开始，超过额度的请求不续期。`limit` 为 1～1000000，`windowSeconds` 为 1～86400。

超限返回 **HTTP 429 / `RATE_LIMITED`**，带 `Retry-After` 秒数。浏览器 CORS 响应会暴露这个头。固定窗口在相邻窗口交界处允许突发，不适合要求严格平滑的场景；后续可替换为令牌桶等算法。

Redis 不可用时默认 **CLOSED**：返回 503，不执行 Controller。仅对允许降级的接口可显式指定：

```java
failurePolicy = RateLimit.FailurePolicy.OPEN
```

OPEN 会记录警告并放行，不代表限流仍然有效。登录防爆破、关键写操作等接口需谨慎选择。

### 可信代理

默认不信任任何 `X-Forwarded-For`，取连接的 remoteAddr；不因为请求头存在就相信它。

可以在 `trusted-proxies` 中配置确切的 IPv4/IPv6 代理地址。V1 不支持 CIDR、主机名、IPv6 zone ID。只有直接连接方可信，才从 X-Forwarded-For 右向左剥离可信代理，取第一个不可信地址；不使用 `X-Real-IP` 或 `Forwarded`。

代理必须正确追加/覆盖转发头，应用也应只允许可信代理接入。不要随意信任整个网络；代理地址动态变化时需采用匹配部署环境的解析方案。

## 4. 短窗口防重复提交

```java
@PreventDuplicateSubmission(windowSeconds = 10)
@PostMapping("/api/articles")
public ApiResponse<?> create(...) { ... }
```

仅允许 POST、PUT、PATCH、DELETE，且始终要求已登录用户。前端提供唯一一个 `Idempotency-Key` 请求头：8～128 位字母、数字、点、下划线、冒号或连字符，首位为字母或数字。UUID 可直接使用。

```javascript
const key = crypto.randomUUID()
await api.post('/articles', payload, {
  headers: { 'Idempotency-Key': key }
})
// 同一次逻辑提交的网络重试沿用 key；用户开始一个新操作时才重新生成。
```

后端通过 Redis `SET NX` 加 TTL 原子占位。隔离维度是 **环境 + 用户 + HTTP 方法 + 映射路径模板 + 请求标识**；例如 `/api/articles/{id}` 的不同 id 共享路径模板。因此，同一用户若复用同一个 key 修改不同资源，也会被拒绝，应为每次独立操作生成新 key。

- 缺失、多值或格式错误：400 / `INVALID_IDEMPOTENCY_KEY`。
- 窗口内重复：409 / `DUPLICATE_SUBMISSION`。
- Redis 故障：503 / `DISTRIBUTED_UNAVAILABLE`，不降级放行。
- 窗口为 1～86400 秒，默认 5 秒。成功、参数校验失败和业务异常都会保留占位直到过期。
- 拦截器在 Controller 参数绑定/校验前运行。修正失败请求后重新提交，应使用一个新的操作 key。
- 不比较请求体，不回放之前的响应，过期后允许再执行。

它只减少短窗口重复执行，**不是持久业务幂等**。客户端每次生成不同 key 可以绕过它；请求执行超过窗口也可能再次进入。支付、下单、消费消息等仍需业务流水、数据库唯一约束和状态机。

同一个接口同时有两种注解时，顺序为：Sa-Token 鉴权 → 限流 → 防重占位 → Controller。重复和非法提交也可能消耗限流额度；不要依靠占位机制提供无限重试。

## 5. 错误处理

上述错误均进入已有标准响应 `ApiResponse`，不把 Redis 地址、堆栈或凭据返回前端。

| HTTP | code | 含义 |
|---|---|---|
| 400 | `INVALID_IDEMPOTENCY_KEY` | 提交标识不合法 |
| 400 | `INVALID_FORWARDED_FOR` | 可信代理传来的地址链不合法 |
| 409 | `LOCK_BUSY` | 未在等待时间内获取锁 |
| 409 | `DUPLICATE_SUBMISSION` | 窗口内已有同一提交标识 |
| 429 | `RATE_LIMITED` | 超限，查看 Retry-After |
| 503 | `DISTRIBUTED_UNAVAILABLE` | 依赖不可用，操作结果需确认 |
| 503 | `LOCK_INTERRUPTED` | 等待锁被中断 |
| 503 | `LOCK_RELEASE_FAILED` | 释放/所有权校验失败，操作结果需确认 |

前端不要对 409、429、503 自动无条件重试写操作。超时或 503 并不表示业务一定没有执行。

## 6. 真实 Redis 集成测试

基础测试不依赖 Redis，运行 `mvn clean verify`。真实 Redis 测试通过独立 profile 显式启动，指定后连接失败就失败，不静默跳过：

```bash
# 先按 README 启动依赖，并向 shell 导入自己的 .env
export TEST_REDIS_PASSWORD="$REDIS_PASSWORD"
mvn -Predis-it clean verify
```

可选变量：`TEST_REDIS_HOST` 默认 127.0.0.1、`TEST_REDIS_PORT` 默认 6379、`TEST_REDIS_DATABASE` 默认 0、`TEST_REDIS_PASSWORD` 默认空。使用本地/测试 Redis，勿指向生产。

测试启动两个独立 Spring Web 上下文与 Redisson 客户端，使用真实 Redis、测试专用 H2 数据库，覆盖共享登录/退出、共享限流和窗口到期、并发防重、异常占位保留、锁互斥/续期/释放/中断、真实连接故障时的 OPEN/CLOSED 策略。

测试 Key 使用随机命名空间，结束后只清理该命名空间；会话主动退出并设置短 TTL。没有 FLUSHDB。下一节使用独立进程验证完整的 Redis 分布式链路；真实 MySQL 的业务 SQL 兼容性需随业务迁移单独验证。

## 7. 双实例演示

演示代码位于 `src/demo`，只由 `distributed-demo` Maven profile 编译。它不属于正式业务模块，不提供生产账号系统。

默认生产输出在 `target`；演示输出独立放在 `target-demo`，避免不同构建相互污染。默认生产包不含演示类和演示配置。**不要将演示包/镜像用于生产。**

前提：JDK 17、Maven、Docker Compose v2、Python 3。所有命令都在 `backend_base` 下执行。

```bash
cp .env.example .env  # 已有 .env 则保留，不要覆盖
# 编辑 .env，设置 REDIS_PASSWORD 和 DEMO_ACCESS_KEY（演示不使用数据库密码）
set -a
source .env
set +a

mvn -Pdistributed-demo clean package -DskipTests
chmod a+r target-demo/backend-base-demo.jar  # Linux/macOS：允许容器的非 root 用户读取
docker compose -f compose.distributed-demo.yaml up -d
python3 scripts/verify_distributed.py
```

`-DskipTests` 只用于演示构建加速；应先执行基础测试和真实 Redis 测试。演示容器以非 root 用户只读挂载本地 `target-demo/backend-base-demo.jar`，直接使用 JRE 17 镜像，无需额外构建应用镜像或在容器内下载 Maven 依赖。重建 JAR 后执行 Compose `up -d --force-recreate`，确保进程重新读取新包。

拓扑：`127.0.0.1:8090 → Nginx → app-a / app-b → Redis`。应用容器不向宿主机开放端口，Nginx 不启用粘滞会话。管理端口仍仅监听应用容器内部回环地址。演示 profile 关闭数据库/Flyway 自动配置，仅验证 Redis 协调能力，不需要 MySQL 或 DB_PASSWORD；默认生产配置仍使用 MySQL，未受影响。

脚本输出五项 PASS：

1. A 创建的登录会话可在 B 使用。
2. A、B 共用五次请求额度，第六次为 429。
3. A、B 并发提交同一 key，恰好一次成功。
4. A、B 竞争同一把锁，一个成功、一个超时；完成后 B 能再次获取。
5. A 退出后 B 返回 401。

演示 `/api/public/demo/login` 要求 `X-Demo-Key`，值来自 `DEMO_ACCESS_KEY`。每次创建随机演示用户，脚本不会打印 Token 或密钥。它用于验证共享会话，不能作为真实登录实现。

Nginx 的 `/a/`、`/b/` 是本地验证直达实例的路由，确保测试真的经过两个进程；其余路径轮询分发。演示没有开启可信代理 IP，按 IP 限流时会把 Nginx 当客户端；如需演示真实 IP 维度，请根据实际网络明确配置代理地址。

查看日志与清理：

```bash
docker compose -f compose.distributed-demo.yaml logs app-a app-b nginx
docker compose -f compose.distributed-demo.yaml down
```

演示使用独立 Compose 项目与 Redis 数据卷，`down` 保留演示 Redis 数据。不要在有需要保留的数据时使用 `-v`。如果 8090 被占用，可调整 Compose 端口，再用 `python3 scripts/verify_distributed.py --base-url http://127.0.0.1:新端口`。

PowerShell 用户按 README 设置环境变量后执行相同 Maven、Compose、Python 命令；验证脚本需要环境变量 `DEMO_ACCESS_KEY`。

## 后续开发计划

V2 缓存、V3 可靠异步处理、V4 运行与观测的范围、实施顺序和验收标准见 [分布式开发路线图](distributed-roadmap.md)。这些阶段尚未实现，当前可用能力以本文 V1 为准。
