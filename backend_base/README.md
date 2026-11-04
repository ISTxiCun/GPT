# Backend Base

**面向分布式应用开发的模块化后端脚手架，提供按需启用、开箱即用的分布式基础能力。**

通过统一配置、可复用组件和可运行的双实例环境，将共享会话、分布式锁、接口限流与短窗口防重复提交接入业务开发流程，减少多实例应用中重复的基础设施建设。

以模块化单体作为开发起点：单 Maven 模块、一个启动入口，业务按包组织，同一应用可部署多个实例。业务模块通过公共 API 和注解使用分布式能力，后续可根据业务边界演进为独立服务。

## 当前能力

| 能力 | 接入方式 | 提供的支持 |
|---|---|---|
| 共享会话 | Sa-Token + Redis，接入项目自己的账号验证 | 多实例共享登录状态和退出结果 |
| 分布式锁 | 注入 `LockExecutor` | 有限等待、看门狗续期和锁释放处理 |
| 接口限流 | Controller 方法添加 `@RateLimit` | 用户、IP、全局维度的跨实例计数 |
| 短窗口防重复提交 | 添加 `@PreventDuplicateSubmission`，前端传递请求标识 | 在约定窗口内原子拦截重复提交 |
| 开发基础 | Web、参数校验、统一异常、分页、数据库迁移、接口文档 | 建立一致的接口与数据访问规范 |
| 双实例验收 | 独立 Compose 环境和验证脚本 | 实际验证会话共享、限流、防重与锁竞争 |

锁、限流和防重默认关闭，可按项目需要分别开启；开启并完成配置后，业务模块即可调用。账号体系、权限数据和业务流程由项目定义，接入方式见第 7、11 节。默认工程的业务模块由开发者扩展，独立演示提供验证分布式行为所需的接口。

“开箱即用”指公共能力、配置和验证环境已配套提供。库存一致性、支付幂等等业务结果，仍需结合数据库约束、事务和业务状态机设计。当前已交付 V1；V2 缓存、V3 可靠异步处理、V4 运行与观测处于规划阶段，进度见[开发路线图](docs/distributed-roadmap.md)。

## 技术基线

| 组件 | 选型 |
|---|---|
| 运行与构建 | JDK 17、Maven 3.9.x |
| 应用框架 | Spring Boot 3.5.9 |
| 数据访问 | MyBatis-Plus、MySQL、Flyway |
| 共享状态与并发协调 | Redis、Redisson |
| 身份认证 | Sa-Token |

项目根目录为 `backend_base`，Maven、Compose 及验证脚本均在该目录执行。

## 阅读入口

- [接入业务开发](#1-环境准备)：准备环境，启动 MySQL、Redis 和应用。
- [分布式能力使用](#11-分布式基础能力)：开启配置、在业务模块中调用。
- [先体验分布式效果](#12-双实例演示)：启动两个应用实例，运行自动验收脚本。
- [分布式详细说明](docs/distributed.md)：算法、异常、事务顺序、代理 IP 和使用限制。
- [后续开发路线图](docs/distributed-roadmap.md)：V2 缓存、V3 异步处理、V4 运行观测的实现计划（尚未实现）。
- [快速生成模块规划](docs/module-generator.md)：18 类可选业务模板、功能勾选与生成流程设计（尚未实现）。
- [架构保护与迁移](docs/architecture-guardrails.md)：会话隔离、错误分层、跨域扩展和自动回归规则。
- [脚手架文档导航](docs/README.md)：当前功能与未来计划的统一入口。

**升级提示：** 会话存储现在按应用/环境统一隔离，原无前缀 Token 需要重新登录；原 `app.distributed.namespace/environment` 配置迁至 `app.identity`。迁移步骤见[架构保护与配置迁移](docs/architecture-guardrails.md#配置与会话迁移)。

## 1. 环境准备

- JDK 17：`java -version`、`mvn -version` 均应指向 JDK 17。
- Maven 3.9.x。
- Docker Engine / Desktop，带 Docker Compose v2；或者自行准备 MySQL 8.4、Redis 7.4。
- 默认应用端口 8080、管理端口 8081、MySQL 3306、Redis 6379，请确认未被占用。

```bash
git clone https://github.com/ISTxiCun/GPT.git
cd GPT/backend_base
cp .env.example .env
```

在 `.env` 中设置 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD` 和 `REDIS_PASSWORD`，供本地数据库及 Redis 使用。第 12 节的独立演示还需设置 `DEMO_ACCESS_KEY`。示例值用于本地体验；实际凭据通过环境配置管理，`.env` 已加入 Git 忽略规则。

## 2. 启动 MySQL 和 Redis

在 `backend_base` 下执行：

```bash
docker compose up -d --wait
docker compose ps
```

Compose 自动创建 `backend_base` 数据库、`backend` 数据库用户、Redis 密码以及持久化卷。数据库和 Redis 端口只绑定本机回环地址。

```bash
docker compose logs mysql redis
docker compose down
```

`down` 保留数据。`docker compose down -v` **会删除本地数据库和 Redis 数据**。修改 `.env` 中 MySQL 密码不会自动更新已有数据卷内的账号；应修改实际账号密码，或在允许丢弃本地数据时重新建卷。

## 3. 启动应用

Compose 从 `.env` 读取容器配置；启动 Java 应用时，将同一份配置导入进程环境，确保应用与依赖服务使用一致的连接信息。

Linux/macOS 的 Bash（仅加载自己维护、可信的 `.env`）：

```bash
set -a
source .env
set +a
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

Windows PowerShell 可设置环境变量；密码须与 `.env` 一致：

```powershell
$env:DB_PASSWORD = '你的数据库密码'
$env:REDIS_PASSWORD = '你的Redis密码'
$env:SPRING_PROFILES_ACTIVE = 'dev'
mvn spring-boot:run
```

IDE：打开当前目录的 `pom.xml`，选择 JDK 17，在运行配置中设置 `DB_PASSWORD`、`REDIS_PASSWORD`、`SPRING_PROFILES_ACTIVE=dev`，运行 `com.istxicun.backend.BackendApplication`。

启动后验证：

```bash
curl -i http://127.0.0.1:8081/actuator/health
```

依赖正常时返回 `{"status":"UP"}`。通过健康检查确认应用及依赖已就绪；Redis 故障会影响会话访问，即使 Web 端口已经监听，健康状态仍可能为 DOWN。管理端口独立于业务端口，默认仅监听 `127.0.0.1`。

开发环境接口文档位于 `http://localhost:8080/swagger-ui/index.html`，OpenAPI JSON 位于 `http://localhost:8080/v3/api-docs`。在 `modules` 中添加 Controller 后，对应接口会出现在文档中。

## 4. 配置项

| 环境变量 | 默认值 / 说明 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | 无；本地开发显式设 `dev` |
| `DB_URL` | `jdbc:mysql://localhost:3306/backend_base?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC` |
| `DB_USERNAME` | `backend` |
| `DB_PASSWORD` | 必填，无默认密码 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `REDIS_PASSWORD` | 必填；已有无密码 Redis 可显式设为空，仅限合适的本地环境 |
| `REDIS_DATABASE` | `0` |
| `SERVER_PORT` | `8080` |
| `MANAGEMENT_PORT` | `8081` |
| `MANAGEMENT_ADDRESS` | `127.0.0.1` |
| `APP_NAMESPACE` | 默认 Spring 应用名；不同项目使用不同值，同项目各实例保持一致 |
| `APP_ENV` | 普通启动必填；dev/demo profile 分别默认 local/demo，生产必须显式设置 |
| `CORS_ALLOWED_HEADERS` | 逗号分隔的额外请求头，自动保留认证、防重等基础头 |
| `CORS_EXPOSED_HEADERS` | 逗号分隔的额外可见响应头，自动保留请求 ID 和 Retry-After |
| `CORS_ALLOWED_ORIGINS` | dev 为 `http://localhost:5173`；其他环境为空，逗号分隔多个完整来源 |

`.env` 的 `MYSQL_ROOT_PASSWORD` 仅用于初始化 MySQL 容器，应用不使用 root 账号。生产环境使用独立凭据和外部配置；按数据库/Redis 服务要求配置 TLS。不要启用 dev profile，接口文档默认关闭。管理端口若调整为对外监听，需要网络隔离与访问控制。

共享状态通过 Redis 管理：普通字符串使用 `StringRedisTemplate`，JSON 数据使用 `ObjectMapper` 按明确类型编解码，避免开放任意类型反序列化。Sa-Token 通过 Redis Jackson 适配器管理会话；业务缓存按项目和模块划分 Key，并设置适合数据时效性的过期策略。

## 5. 目录与模块约定

```text
backend_base/
├── pom.xml
├── src/main/java/com/istxicun/backend/
│   ├── BackendApplication.java
│   ├── common/                  # 响应、分页、异常
│   ├── config/                  # Web、认证、Redis、MP、文档、请求 ID
│   ├── distributed/             # 分布式公共组件：锁、限流、防重
│   └── modules/                 # 按业务边界扩展功能模块
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/migration/            # Flyway SQL
├── src/demo/                    # 独立 profile 编译的演示代码
├── src/test/
├── compose.yaml                 # 日常开发：MySQL、Redis
├── compose.distributed-demo.yaml # 独立演示：Redis、双实例、Nginx
├── demo/nginx.conf
├── scripts/verify_distributed.py
├── docs/distributed.md
└── Dockerfile
```

在 `modules` 下按业务边界新增模块，例如 `modules.article`，根据实际复杂度组织 `controller`、`service`、`mapper`、`entity` 和 `dto`。保持每层职责清晰，减少空层级与重复转发。

- Controller 接收 DTO、执行参数校验，Service 承载业务与事务，Mapper 负责本模块数据访问。
- Mapper 接口标注 `@Mapper` 并继承 `BaseMapper<Entity>`；当前不使用宽泛的全包 Mapper 扫描。
- 自定义 XML 放 `src/main/resources/mapper/<模块>/`，namespace 对应 Mapper 全类名。
- 业务模块通过公开 Service 与 DTO 协作，不调用其他模块 Mapper，不直接暴露数据库实体作为 API。
- 本模块事务在公开 Service 方法上使用 `@Transactional`，注意代理自调用不生效。
- `common` 存放稳定的共享类型；分布式机制集中在 `distributed`，业务规则归属各业务模块。
- 通过上述开发约定保持模块边界，当前由开发者维护依赖关系。拆分服务时，再将模块协作扩展为具备超时、幂等和一致性策略的远程调用。

基础包名可按实际项目调整：同步修改 Java 包声明、目录、启动类和测试包。Maven 坐标在 `pom.xml` 修改，Dockerfile 中 JAR 文件名也需同步。

## 6. 数据库与分页

Flyway 在应用启动时执行 `db/migration` 下的版本迁移，统一管理各环境的数据库结构。初始工程保留迁移入口和历史管理，业务表随模块开发逐步加入。

新增表时创建 `V1__create_article.sql`，后续变更使用 `V2__...sql`。已在共享环境执行的脚本不要修改。已有数据库需人工制定基线方案；默认不开启 `baseline-on-migrate`，也禁止 clean。

MyBatis-Plus 已配置 MySQL 分页插件，最大每页 100 条：

```java
@GetMapping
public ApiResponse<PageResult<ArticleDto>> list(@Valid @ModelAttribute PageQuery query) {
    return ApiResponse.ok(articleService.page(query));
}
// Service 中：mapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper)
// 转为 DTO 后使用 PageResult.from(dtoPage)。ArticleDto / articleService 由业务模块实现。
```

`page` 默认 1，`size` 默认 20；需使用 `@Valid` 才会校验 1～100 的范围。查询条件和排序字段必须由后端白名单构造，不把前端输入拼入 SQL。

主键默认采用数据库自增。跨库唯一 ID、逻辑删除、乐观锁和审计字段由业务的数据模型决定，并通过迁移脚本落实；这些机制在需要时接入。

## 7. Sa-Token 如何接入业务

已接入 Sa-Token Web 拦截器与 Redis 会话存储：

- `/api/**` 默认要求登录；`/api/public/**` 显式公开。
- `@SaIgnore` 可让某个 API 方法跳过认证，请谨慎使用。
- 认证仅保护上述 API 路径，业务 Controller 必须使用 `/api` 前缀。
- 文档只在 dev profile 开启；健康检查位于独立管理端口。
- Token 从请求头 `Authorization: Bearer <token>` 读取，不读取 Cookie 或表单。
- 登录有效期为 7200 秒，允许并发登录，每次登录生成独立 Token；没有刷新令牌接口。

账号验证由业务模块实现；验证通过后，调用 Sa-Token 建立共享会话：

```java
// 放在 /api/public/login 对应的业务 Service 中：
// 先校验账号状态，并使用可靠的单向密码哈希方案验证密码。
StpUtil.login(userId);
String token = StpUtil.getTokenValue(); // 返回原始 Token，Vue 自行添加 Bearer 前缀
```

业务模块负责账号状态、密码哈希验证及所需的验证码流程，再用服务端确认的 userId 建立会话。登录防护可结合第 11 节的 IP 或全局限流；第 12 节的演示登录仅用于验证跨实例共享行为。

退出接口放在受保护路径，调用 `StpUtil.logout()`；当前用户 ID 用 `StpUtil.getLoginId()` 获取。不要信任请求体中的当前用户 ID。

使用 `@SaCheckPermission("article:write")` 声明接口权限。由业务模块提供 `StpInterface` Bean，实现 `getPermissionList` 和 `getRoleList`，将项目自己的角色、权限数据接入统一校验流程。

共享会话让不同实例识别同一登录身份。涉及资源并发和重复操作时，继续组合第 11 节的公共能力，并在业务服务中落实相应的数据约束与事务规则。

## 8. Vue 对接

建议开发环境通过 Vite 代理 `/api` 到 `http://localhost:8080`；也可直接请求后端并配置 CORS。来源必须匹配协议、主机、端口，`127.0.0.1` 与 `localhost` 是不同来源。业务自定义请求头通过 `CORS_ALLOWED_HEADERS` 扩展，可见响应头通过 `CORS_EXPOSED_HEADERS` 扩展，无需修改公共代码；完整 YAML 示例见[架构保护文档](docs/architecture-guardrails.md#3-可扩展-cors)。

Axios 示例（假设 tokenStore 是你的登录状态存储）：

```javascript
const api = axios.create({ baseURL: '/api', timeout: 10000 })
api.interceptors.request.use(config => {
  const token = tokenStore.token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      tokenStore.clear()
      // 按前端路由规则跳转登录页
    }
    return Promise.reject(error)
  }
)
```

按前端安全策略保存 Token，控制日志输出并防范 XSS。当前通过请求头传递会话凭据，Axios 通常保持 `withCredentials` 关闭即可。

普通 JSON 接口通过 `ApiResponse.ok(data)` 返回统一结构；文件流和第三方协议响应保留各自的格式：

```json
{"code":"OK","message":"success","data":{},"requestId":"..."}
```

HTTP 状态与业务码共同表达处理结果：参数/业务校验失败为 400，未登录为 401，无权限为 403，资源不存在为 404，未知异常为 500。前端根据状态码和业务码分别处理成功与失败。业务错误可抛 `new BusinessException("ARTICLE_UNAVAILABLE", "文章暂不可用")`，可见消息不得包含敏感数据。

每个请求生成 `X-Request-Id`，并写入日志 MDC 和标准响应，便于关联请求与日志。完整分布式追踪计划在 V4 接入；当前日志默认省略请求体和 Token。

## 9. 测试与打包

```bash
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 或设置环境变量后运行打包产物
java -jar target/backend-base-0.0.1-SNAPSHOT.jar
```

测试使用仅测试范围的 H2，不需要本地 MySQL/Redis；覆盖应用上下文启动、未登录拦截、公开接口、参数校验、CORS 预检与拒绝、错误响应及默认文档关闭。测试 Controller 只存在于 `src/test`，不会打进生产包。

配置测试还覆盖功能默认关闭、环境必填、注解误用拒绝启动与可信代理解析。`mvn -Predis-it clean verify` 可运行真实 Redis 集成测试，详见 [分布式文档](docs/distributed.md#6-真实-redis-集成测试)。

默认测试还包含架构边界、会话配置保护、CORS 扩展与 HTTP 错误映射检查。真实 Redis 测试使用独立 JVM 验证共享会话及跨项目/环境隔离；GitHub Actions 自动执行这些回归规则。

基础测试不替代真实 MySQL/Redis 验证；新增业务后应补充真实数据库、迁移和 Sa-Token 登录/退出/权限测试。H2 不保证 MySQL 特有 SQL 兼容。

## 10. Docker 运行应用

在当前目录执行，先按第 2 节启动依赖：

```bash
docker build -t backend-base:local .
docker run --rm --name backend-base-app \
  --network backend-base-local \
  --env-file .env \
  -e DB_URL='jdbc:mysql://mysql:3306/backend_base?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC' \
  -e REDIS_HOST=redis \
  -p 127.0.0.1:8080:8080 \
  backend-base:local
```

应用容器用服务名访问数据库，不能使用 localhost。镜像采用 JDK 17 构建、JRE 17 运行，非 root 用户；Docker 构建跳过测试，应先执行 `mvn clean verify`。默认不开 dev，也不会开放接口文档；管理端口在容器内部回环地址上，不映射宿主机。编排系统需要探针时，自行配置管理监听地址，并保持管理端口只允许内部访问。

## 11. 分布式基础能力

V1 将跨实例协作所需的公共机制集中在 `com.istxicun.backend.distributed`。业务模块通过配置、注解和显式 API 接入，各应用实例共同使用 Redis 完成状态共享与并发协调。

| 能力 | 入口 | 默认行为 |
|---|---|---|
| 分布式锁 | 注入 `LockExecutor` | 有限等待、看门狗续期、异常后释放 |
| 固定窗口限流 | Controller 方法上的 `@RateLimit` | 按用户计数，支持 IP / 全局维度；超限返回 429 |
| 短窗口防重复提交 | Controller 方法上的 `@PreventDuplicateSubmission` | 要求登录和 `Idempotency-Key`，重复返回 409 |

### 11.1 开启配置

在自己的环境配置文件中添加，例如 `application-dev.yml`，然后按第 3 节启动应用：

```yaml
app:
  identity:
    namespace: ${APP_NAMESPACE:${spring.application.name}}
    environment: ${APP_ENV:}
  distributed:
    lock:
      enabled: true
    rate-limit:
      enabled: true
    submission:
      enabled: true
```

启动前设置 `APP_ENV=local`；PowerShell 使用 `$env:APP_ENV = 'local'`，IDE 则加入运行环境变量。上面的三个开关可以分别开启，未配置时全部关闭。连接继续使用第 4 节的 Redis 配置，V1 锁适配器支持 standalone Redis。

会话与分布式组件统一使用 `app.identity`。同一部署的多个实例使用相同的 namespace、APP_ENV 和 Redis 配置；不同项目/环境使用不同标识。接口用了注解却没有开启对应功能，启动时会明确报错，避免保护静默失效。

### 11.2 在业务模块中调用锁

在自己的 Service 中通过构造器注入：

```java
import com.istxicun.backend.distributed.lock.LockExecutor;
import java.time.Duration;

// lockExecutor 为构造器注入的字段；resourceId、command 和 transactionalService 由业务实现。
return lockExecutor.execute(
    "resource:update:" + resourceId,
    Duration.ofSeconds(2),
    () -> transactionalService.update(command)
);
```

锁应在事务外获取，事务在回调内另一个 Spring Bean 的 `@Transactional` 方法中完成，再释放锁。检测到外层已有活动事务时执行器会拒绝执行。回调必须在当前线程同步完成；不能启动异步任务后立即返回。获取失败返回 409 / `LOCK_BUSY`，依赖故障返回 503。

### 11.3 在接口上使用限流与防重

将下面的方法放入自己的 `@RestController`，导入对应类型：

```java
import com.istxicun.backend.common.api.ApiResponse;
import com.istxicun.backend.distributed.ratelimit.RateLimit;
import com.istxicun.backend.distributed.submission.PreventDuplicateSubmission;
import org.springframework.web.bind.annotation.PostMapping;

@RateLimit(key = "resource:create", limit = 10, windowSeconds = 60)
@PreventDuplicateSubmission(windowSeconds = 10)
@PostMapping("/api/resources")
public ApiResponse<String> create() {
    // 在此调用自己的业务 Service。
    return ApiResponse.ok("accepted");
}
```

`@RateLimit` 默认按当前登录用户计数；可设置 `dimension = RateLimit.Dimension.IP` 或 `GLOBAL`。Redis 不可用时默认拒绝请求并返回 503；只有允许降级的接口才显式使用 `failurePolicy = RateLimit.FailurePolicy.OPEN`。超限返回 429，并附带 `Retry-After` 秒数。

防重接口由 Vue 在请求头中提供标识：

```javascript
const submissionKey = crypto.randomUUID()
await api.post('/resources', payload, {
  headers: { 'Idempotency-Key': submissionKey }
})
// 同一次逻辑提交的网络重试复用 submissionKey；新的操作生成新的 key。
```

标识格式为 8～128 位字母、数字、点、下划线、冒号或连字符，首位为字母或数字。缺失或格式错误返回 400，窗口内重复返回 409。成功或业务失败都会保留占位直到过期，不回放之前的响应。

两种注解只支持 `/api/**` 下的同步 MVC Controller 方法；异步返回值在启动时会被拒绝。执行顺序为鉴权 → 限流 → 防重 → Controller。

**将公共机制与业务约束配合使用。** 锁与数据库条件更新、唯一约束共同控制关键写入；下单、支付等持久业务幂等还需业务流水或状态机。完整语义、错误码和可信代理配置见 [分布式详细说明](docs/distributed.md)。

## 12. 双实例演示

通过独立演示，可在业务开发前验证分布式能力的实际效果。Compose 会启动 Redis、两个应用实例和 Nginx；演示 profile 关闭数据库/Flyway 自动配置，可直接执行本节步骤。业务开发环境按第 2、3 节使用 MySQL 和 Redis。

前提：JDK 17、Maven、Docker Compose v2、Python 3。以下命令在 `backend_base` 中执行，使用 Bash：

```bash
# 仅首次创建；已有 .env 时不要覆盖。
cp .env.example .env
# 编辑 .env，设置 REDIS_PASSWORD 和 DEMO_ACCESS_KEY。
set -a
source .env
set +a

# 正常开发建议先运行第 9 节的基础测试。
mvn -Pdistributed-demo clean package -DskipTests
chmod a+r target-demo/backend-base-demo.jar

docker compose -f compose.distributed-demo.yaml up -d
python3 scripts/verify_distributed.py
```

`chmod` 用于让容器的非 root 用户读取 JAR。PowerShell 用户按第 3 节设置环境变量，并额外设置 `$env:DEMO_ACCESS_KEY`，然后运行相同 Maven、Compose 和 Python 命令；文件权限按 Docker Desktop 的共享设置处理。

演示入口为 `http://127.0.0.1:8090`，链路为 Nginx → 实例 A / B → Redis。验收脚本输出五项 PASS：跨实例登录、共享限流额度、并发防重、锁竞争与释放、跨实例退出。

```bash
# 查看状态与日志
docker compose -f compose.distributed-demo.yaml ps
docker compose -f compose.distributed-demo.yaml logs app-a app-b nginx
# 停止演示；保留演示 Redis 数据卷
docker compose -f compose.distributed-demo.yaml down
```

演示代码在 `src/demo`，只有 `distributed-demo` Maven profile 编译它，输出目录为 `target-demo`；默认生产包在 `target`，不含演示接口。演示登录需要 `X-Demo-Key`，仅用于本地验证。不要把演示包用于生产。重建演示 JAR 后执行 `up -d --force-recreate` 重新加载。

要运行真实 Redis 集成测试，先按第 2 节启动开发 Redis 并导入自己的 `.env`：

```bash
export TEST_REDIS_PASSWORD="$REDIS_PASSWORD"
mvn -Predis-it clean verify
```

该测试通过本机 6379 端口连接开发 Redis，和没有开放 Redis 宿主机端口的独立演示环境分开。自定义测试地址及测试覆盖范围见 [真实 Redis 测试说明](docs/distributed.md#6-真实-redis-集成测试)。

## 13. 常见问题

- **环境或旧隔离配置报错**：设置 APP_ENV；将旧的 `app.distributed.namespace/environment` 改为 `app.identity`。dev 默认 local，生产环境显式指定。
- **启动报密码占位符无法解析**：Java 进程没有读取 `.env`，请导出 `DB_PASSWORD` 和 `REDIS_PASSWORD`。
- **连接数据库失败**：检查 Compose 健康状态、端口、实际账号密码；容器内用 `mysql`，宿主机用 `localhost`。
- **Redis AUTH 失败**：应用环境变量必须与 Redis 实际密码一致；重建/更新 Redis 配置后重试。
- **接口 401**：按第 7 节接入账号验证和会话建立，并检查请求是否携带有效 Bearer Token。
- **权限注解不通过**：确认实现 `StpInterface` 并返回登录用户实际权限。
- **浏览器跨域失败**：检查 `CORS_ALLOWED_ORIGINS`，或使用 Vite 代理。CORS 不是后端授权机制。
- **访问根路径 404**：默认工程从后端接口开发开始；添加业务 Controller 后访问对应的 `/api/**` 路径，体验现成功能可使用第 12 节演示。
- **生产文档 404**：默认关闭，属于预期行为。
- **分布式功能启动失败**：确认已设置 `APP_ENV`，并开启接口注解所需的开关；检查是否误用了异步 Controller 返回值。
- **演示提示 Unable to access jarfile**：先构建 `target-demo/backend-base-demo.jar`，并保证容器用户有读取权限；Bash 可执行第 12 节的 `chmod`。
- **限流 429 / 防重 409**：这是保护机制的预期响应，按 Retry-After 或业务规则处理，避免无条件重试写操作。
- **迁移校验失败**：检查是否修改了已执行迁移；不要直接删除历史表或随意执行 repair。

## 14. 演进路线与业务职责

以“启用公共能力 → 接入业务模块 → 双实例验证 → 按需演进”为开发路径。先在模块化单体中完成业务和多实例部署，再根据实际边界推进服务拆分。

| 阶段 | 方向 | 当前状态 |
|---|---|---|
| V1 | 共享会话接入、分布式锁、限流、短窗口防重和双实例验收 | 已实现 |
| V2 | 缓存入口、TTL 策略、空值缓存、热点加载保护 | 规划中 |
| V3 | 可靠异步处理、消费幂等扩展、可选 Outbox | 规划中 |
| V4 | 指标、链路、健康探针、停机与故障验证 | 规划中 |

V2～V4 的接口、实施顺序和验收标准记录在[开发路线图](docs/distributed-roadmap.md)，实现进度以代码与对应阶段文档为准。

多实例部署时，各实例保持一致的会话与共享组件配置，需要共享的状态放在相应外部服务中。业务模块负责数据归属、权限、事务、持久幂等和状态流转；脚手架负责提供可复用的分布式机制及接入方式。两者共同构成可验证的业务实现。

### V2 显式缓存

已提供 [CacheClient 使用与故障语义](docs/distributed-v2-cache.md)。
默认关闭，通过 `app.distributed.cache.enabled` 开启，热点保护需要同时开启 V1 锁。
支持泛型三态读取、TTL 抖动、空值缓存、有界热点加载和提交后失效；不提供强一致保证。
显式开启的读故障降级覆盖首次、锁内及等待锁后的读取，降级回源在释放加载锁后受并发预算约束。

### V3 可靠异步消息

[消息接入、迁移、失败恢复与演示](docs/distributed-v3-messaging.md)提供 RabbitMQ 4.1 quorum 队列、
confirm/return 发布结果、手动 ACK、有界重试、授权审计重放、MySQL 消费幂等与可选 Outbox。
`app.distributed.messaging.enabled`、`consumers-enabled` 和 `outbox.enabled` 均默认关闭；
启用 JDBC 能力须显式增加 `classpath:db/messaging` 迁移位置。语义为至少一次，业务负责幂等。
验证用 `mvn -Pmessaging-it verify`，演示依赖见 `compose.messaging-demo.yaml`。
