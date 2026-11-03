# Backend Base

基于 **JDK 17 / Spring Boot 3.5.9 / MyBatis-Plus / MySQL / Redis / Sa-Token** 的通用后端脚手架。单 Maven 模块、一个启动入口，业务按包模块化。项目根目录就是 `backend_base`。

提供 Web、数据库、认证框架与开发环境基础配置，以及默认关闭、按需启用的分布式锁、限流、防重复提交能力。**不包含登录接口、账号表、用户管理、角色管理或电商业务**，也没有默认账号、演示 Token、业务数据库表。启动成功后，业务接口为空是正常现象。

## 阅读入口

- [本地启动](#1-环境准备)：启动 MySQL、Redis 和应用。
- [分布式能力使用](#11-分布式基础能力)：开启配置、在业务模块中调用。
- [双实例演示](#12-双实例演示)：启动两个应用实例，运行自动验收脚本。
- [分布式详细说明](docs/distributed.md)：算法、异常、事务顺序、代理 IP 和使用限制。

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

修改 `.env` 中的 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD`。`DEMO_ACCESS_KEY` 仅用于第 12 节的可选演示，普通应用不需要它。示例值仅供本地开发；`.env` 已被 Git 忽略，勿提交真实凭据。

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

Spring Boot 不会自动读取 `.env`。Compose 读取它只供容器使用，Java 进程还需要环境变量。

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

依赖正常时返回 `{"status":"UP"}`。Redis 不可用时 Web 服务可能已启动，但健康检查为 DOWN，认证操作也会失败；以健康检查作为就绪依据。管理端口默认只监听 `127.0.0.1`，不会混在业务端口上。

开发环境接口文档：`http://localhost:8080/swagger-ui/index.html`，OpenAPI JSON：`http://localhost:8080/v3/api-docs`。目前没有业务接口，因此文档没有业务操作。

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
| `APP_ENV` | 普通启动可不设；开启任一分布式能力后必填，如 `local`、`test`、`prod` |
| `CORS_ALLOWED_ORIGINS` | dev 为 `http://localhost:5173`；其他环境为空，逗号分隔多个完整来源 |

`.env` 的 `MYSQL_ROOT_PASSWORD` 仅用于初始化 MySQL 容器，应用不使用 root 账号。生产环境使用独立凭据和外部配置；按数据库/Redis 服务要求配置 TLS。不要启用 dev profile，接口文档默认关闭。管理端口若调整为对外监听，需要网络隔离与访问控制。

Redis 的 `StringRedisTemplate` 用于字符串操作；JSON 值请显式用 `ObjectMapper` 编解码为字符串，不开放任意类型反序列化。Sa-Token 使用自己的 Redis Jackson 适配器管理会话。业务缓存使用明确的项目/模块 key 前缀与过期策略。

## 5. 目录与模块约定

```text
backend_base/
├── pom.xml
├── src/main/java/com/istxicun/backend/
│   ├── BackendApplication.java
│   ├── common/                  # 响应、分页、异常
│   ├── config/                  # Web、认证、Redis、MP、文档、请求 ID
│   ├── distributed/             # 可选锁、限流、防重复提交
│   └── modules/                 # 后续业务模块
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

新增模块时，例如 `modules.article`，按需建立 `controller`、`service`、`mapper`、`entity`、`dto`；简单模块不强制建立空层级。

- Controller 接收 DTO、执行参数校验，Service 承载业务与事务，Mapper 负责本模块数据访问。
- Mapper 接口标注 `@Mapper` 并继承 `BaseMapper<Entity>`；当前不使用宽泛的全包 Mapper 扫描。
- 自定义 XML 放 `src/main/resources/mapper/<模块>/`，namespace 对应 Mapper 全类名。
- 业务模块通过公开 Service 与 DTO 协作，不调用其他模块 Mapper，不直接暴露数据库实体作为 API。
- 本模块事务在公开 Service 方法上使用 `@Transactional`，注意代理自调用不生效。
- `common` 只收纳稳定的共享类型，不堆入业务服务。
- 当前没有强制边界检查；这些是开发约定。拆服务时需重新设计远程调用、超时、幂等和跨服务一致性。

基础包名可按实际项目调整：同步修改 Java 包声明、目录、启动类和测试包。Maven 坐标在 `pom.xml` 修改，Dockerfile 中 JAR 文件名也需同步。

## 6. 数据库与分页

Flyway 在启动时连接数据库并执行 `db/migration` 下迁移。当前没有业务 SQL，只会维护迁移历史，不创建示例业务表。

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

主键默认自增；分布式 ID、逻辑删除、乐观锁、创建/更新时间自动填充均未预设。按具体业务选择并通过迁移落实，避免脚手架替所有表规定字段。

## 7. Sa-Token 如何接入业务

已接入 Sa-Token Web 拦截器与 Redis 会话存储：

- `/api/**` 默认要求登录；`/api/public/**` 显式公开。
- `@SaIgnore` 可让某个 API 方法跳过认证，请谨慎使用。
- 认证仅保护上述 API 路径，业务 Controller 必须使用 `/api` 前缀。
- 文档只在 dev profile 开启；健康检查位于独立管理端口。
- Token 从请求头 `Authorization: Bearer <token>` 读取，不读取 Cookie 或表单。
- 登录有效期为 7200 秒，允许并发登录，每次登录生成独立 Token；没有刷新令牌接口。

项目实现账号验证后，才调用：

```java
// 放在 /api/public/login 对应的业务 Service 中：
// 先校验账号状态，并使用可靠的单向密码哈希方案验证密码。
StpUtil.login(userId);
String token = StpUtil.getTokenValue(); // 返回原始 Token，Vue 自行添加 Bearer 前缀
```

不要直接把客户端传来的 userId 当作已验证身份。密码算法、验证码、登录限流等需要由项目实现，不提供硬编码的演示登录接口。

退出接口放在受保护路径，调用 `StpUtil.logout()`；当前用户 ID 用 `StpUtil.getLoginId()` 获取。不要信任请求体中的当前用户 ID。

权限注解使用 `@SaCheckPermission("article:write")`。业务项目必须提供 `StpInterface` Bean，实现 `getPermissionList`、`getRoleList`，从自己的数据模型查询权限；脚手架不提供“所有人全部权限”的兜底实现，也不创建权限表。

会话共享只解决多实例登录状态。它不自动提供库存并发控制、业务幂等、缓存一致性或分布式事务。

## 8. Vue 对接

建议开发环境通过 Vite 代理 `/api` 到 `http://localhost:8080`；也可直接请求后端并配置 CORS。来源必须匹配协议、主机、端口，`127.0.0.1` 与 `localhost` 是不同来源。

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

Token 需按前端安全策略存储，避免日志泄漏及 XSS 窃取。这里没有 Cookie 会话，通常无需开启 Axios `withCredentials`。

普通 JSON API 主动返回 `ApiResponse.ok(data)`，没有全局强制包装下载流或第三方响应：

```json
{"code":"OK","message":"success","data":{},"requestId":"..."}
```

HTTP 状态与业务码同时表达错误：参数/业务校验 400，未登录 401，无权限 403，资源不存在 404，未知异常 500。前端应处理非 2xx，不是所有结果都返回 HTTP 200。业务错误可抛 `new BusinessException("ARTICLE_UNAVAILABLE", "文章暂不可用")`，可见消息不得包含敏感数据。

每个请求生成 `X-Request-Id`，同一个 ID 写入日志 MDC 和标准响应，用于定位问题；这不是分布式链路追踪。日志不默认打印请求体或 Token。

## 9. 测试与打包

```bash
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 或设置环境变量后运行打包产物
java -jar target/backend-base-0.0.1-SNAPSHOT.jar
```

测试使用仅测试范围的 H2，不需要本地 MySQL/Redis；覆盖应用上下文启动、未登录拦截、公开接口、参数校验、CORS 预检与拒绝、错误响应及默认文档关闭。测试 Controller 只存在于 `src/test`，不会打进生产包。

配置测试还覆盖功能默认关闭、环境必填、注解误用拒绝启动与可信代理解析。`mvn -Predis-it clean verify` 可运行真实 Redis 集成测试，详见 [分布式文档](docs/distributed.md#6-真实-redis-集成测试)。

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

三项能力均放在 `com.istxicun.backend.distributed`，由业务模块调用，不需要单独部署公共服务。

| 能力 | 入口 | 默认行为 |
|---|---|---|
| 分布式锁 | 注入 `LockExecutor` | 有限等待、看门狗续期、异常后释放 |
| 固定窗口限流 | Controller 方法上的 `@RateLimit` | 按用户计数，支持 IP / 全局维度；超限返回 429 |
| 短窗口防重复提交 | Controller 方法上的 `@PreventDuplicateSubmission` | 要求登录和 `Idempotency-Key`，重复返回 409 |

### 11.1 开启配置

在自己的环境配置文件中添加，例如 `application-dev.yml`，然后按第 3 节启动应用：

```yaml
app:
  distributed:
    environment: ${APP_ENV:}
    lock:
      enabled: true
    rate-limit:
      enabled: true
    submission:
      enabled: true
```

启动前设置 `APP_ENV=local`；PowerShell 使用 `$env:APP_ENV = 'local'`，IDE 则加入运行环境变量。上面的三个开关可以分别开启，未配置时全部关闭。连接继续使用第 4 节的 Redis 配置，V1 锁适配器支持 standalone Redis。

同一部署的多个实例使用相同的应用名、APP_ENV、Redis 配置；不同项目或环境必须隔离。接口用了注解却没有开启对应功能，启动时会明确报错，避免保护静默失效。

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

**这些能力不代替业务约束。** 锁仍需结合数据库条件更新或唯一约束；短窗口防重不等于下单、支付等持久业务幂等。完整语义、错误码和可信代理配置见 [分布式详细说明](docs/distributed.md)。

## 12. 双实例演示

如果想先验证效果，可以运行独立演示。它只需要 Redis，Compose 会启动两个应用实例和 Nginx；无需先启动第 2 节的 MySQL。演示 profile 关闭数据库/Flyway 自动配置，普通应用仍使用 MySQL。

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

- **启动报密码占位符无法解析**：Java 进程没有读取 `.env`，请导出 `DB_PASSWORD` 和 `REDIS_PASSWORD`。
- **连接数据库失败**：检查 Compose 健康状态、端口、实际账号密码；容器内用 `mysql`，宿主机用 `localhost`。
- **Redis AUTH 失败**：应用环境变量必须与 Redis 实际密码一致；重建/更新 Redis 配置后重试。
- **接口 401**：尚未实现登录，或未携带有效 Bearer Token；查看第 7 节。不要直接取消全局认证来解决。
- **权限注解不通过**：确认实现 `StpInterface` 并返回登录用户实际权限。
- **浏览器跨域失败**：检查 `CORS_ALLOWED_ORIGINS`，或使用 Vite 代理。CORS 不是后端授权机制。
- **访问根路径 404**：没有首页与业务 Controller，这是基础脚手架的预期行为。
- **生产文档 404**：默认关闭，属于预期行为。
- **分布式功能启动失败**：确认已设置 `APP_ENV`，并开启接口注解所需的开关；检查是否误用了异步 Controller 返回值。
- **演示提示 Unable to access jarfile**：先构建 `target-demo/backend-base-demo.jar`，并保证容器用户有读取权限；Bash 可执行第 12 节的 `chmod`。
- **限流 429 / 防重 409**：这是保护机制的预期响应，按 Retry-After 或业务规则处理，避免无条件重试写操作。
- **迁移校验失败**：检查是否修改了已执行迁移；不要直接删除历史表或随意执行 repair。

## 14. 后续扩展边界

可以从单体开始增加业务模块，再部署多个实例；所有实例使用同一套 Sa-Token 配置和 Redis，数据库由部署方案统一管理。不要依赖本机内存保存需要共享的状态。

已提供可选分布式锁、固定窗口限流、短窗口防重复提交，具体使用及限制见 [分布式基础能力 V1](docs/distributed.md)。缓存一致性、消息队列、持久业务幂等、对象存储、定时任务与服务拆分仍由项目按需实现。公共能力不替代业务约束，也不承诺分布式业务正确性。
