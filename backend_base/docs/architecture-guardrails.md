# 架构保护与配置迁移

本文记录会话隔离、公共异常边界和 CORS 扩展的实施约束及自动化保护。它们随默认 Maven 测试运行，真实会话隔离另由 `redis-it` 覆盖。后续模块生成器和公共能力必须遵守同样的约定。

## 1. 统一应用与环境标识

唯一配置入口：

```yaml
app:
  identity:
    namespace: ${APP_NAMESPACE:${spring.application.name}}
    environment: ${APP_ENV:}
```

`ApplicationIdentity` 对两项进行校验，均为 1～64 位字母、数字、下划线或连字符。namespace 默认取 Spring 应用名；不同项目须使用不同的 APP_NAMESPACE 或应用名，不同环境须使用不同 APP_ENV，同项目同环境的实例保持一致。

普通启动必须明确环境，和锁/限流/防重是否开启无关。`dev` profile 在未设置 APP_ENV 时使用 `local`，独立演示使用 `demo`；生产/其他 profile 应显式设置 APP_ENV。`.env.example` 提供本地值，部署时按目标环境配置。

物理 Redis Key：

- 会话：`namespace:environment:session:<Sa-Token logical key>`。
- 锁/限流/防重：`namespace:environment:dist:<feature>:<digest>`。

会话通过专用 Redis Key serializer 添加/去除物理前缀，逻辑 Session ID 和客户端 `Authorization: Bearer ...` 协议保持不变。字符串、对象、Session、TTL 和搜索均经过同一适配器；跨环境的搜索不读取其他前缀。

启动时校验只能存在一个受支持的 namespaced SaTokenDao、它使用同一应用标识且没有被换成无隔离 serializer。排除上游无前缀 DAO 自动配置，禁止应用代码运行期改写全局 DAO，存储适配包之外的业务代码通过 Sa-Token 公共 API 操作会话，不直接访问 DAO。命名空间提供应用层逻辑隔离；Redis 凭据/ACL 仍由部署管理。

### 配置与会话迁移

1. 将原 `app.distributed.namespace/environment` 移至 `app.identity.namespace/environment`，删除旧键。旧键会导致绑定失败，避免看起来配置了隔离、实际未生效。
2. 为每个项目、环境确定唯一 namespace/environment，所有实例一起采用相同新配置。避免混跑新旧会话键策略造成登录表现不一致。
3. **原有无前缀 Token 在新版本中不可用，需要重新登录。** 不回退兼容无前缀键，否则会重新引入跨环境识别。旧数据按原 TTL 到期，勿对共享 Redis 执行清库。
4. 保持 namespace/environment 稳定；变更任一项会切换会话与分布式键空间，应作为有状态配置变更安排发布。

## 2. 与传输协议无关的错误

- `ApplicationException` 位于 common，只携带 `FailureCategory`、错误码、可读消息、可选重试秒数和 cause。
- `DistributedException` 继承该契约。锁、限流、防重等核心不返回 HttpStatus/ResponseEntity，也不读取 Servlet 请求。
- `web.error.HttpFailureMapper` 将分类映射为 HTTP，`web.error.GlobalExceptionHandler` 形成现有 ApiResponse 和 Retry-After。已有 HTTP 状态和业务码保持一致。
- MQ 消费者、调度任务等调用方将分类映射成自己的重试、失败或告警行为；不能照搬 HTTP 响应作为执行结果。

分类映射使用穷尽 enum switch，新增类别时必须明确 Web 映射；回归测试验证各分类响应及重试头。common 对具体功能的反向依赖、核心对 HTTP/Web 的依赖，由 ArchUnit 自动阻止。

原 `DistributedException(HttpStatus, ...)` / `getStatus()` 改为 `DistributedException(FailureCategory, ...)` / `getCategory()`；重试信息使用 `getRetryAfterSeconds()`。HTTP Advice 从 common 移至 web.error，定制代码需要同步 import。

## 3. 可扩展 CORS

```yaml
app:
  cors:
    allowed-origins: https://frontend.example
    allowed-headers:
      - Content-Range
      - X-Upload-Id
    exposed-headers:
      - X-Upload-Offset
    max-age-seconds: 3600
```

列表按**扩展**解释：框架始终保留 Authorization、Content-Type、X-Request-Id、Idempotency-Key，以及响应暴露的 X-Request-Id、Retry-After。合并不区分大小写、重复项只保留一份。配置的业务头不需要修改 WebConfig。

支持 `CORS_ALLOWED_HEADERS`、`CORS_EXPOSED_HEADERS` 以逗号分隔扩展；来源继续使用 `CORS_ALLOWED_ORIGINS`。当前仍采用 Bearer 请求头和 `allowCredentials=false`。

头名需要合法、显式、有数量与长度上限；禁止通配符和控制字符，非法配置/未知字段启动报错。预检先于认证执行，未允许的来源和头继续拒绝。测试覆盖配置扩展、必需头保留、大小写去重、浏览器预检及可见响应头。

## 4. 自动化保护

| 保护 | 执行时机 | 阻止的回退 |
|---|---|---|
| ApplicationIdentity 校验 | 每次启动 | 空/非法环境和相互独立的隔离配置 |
| Session DAO guard | 每次启动 | 多 DAO、替换 serializer、使用不同标识的 DAO |
| ArchUnit 架构测试 | `mvn verify` | common 反向依赖、核心 HTTP/Web 耦合、手动改写 SaManager DAO |
| 配置/HTTP 契约测试 | `mvn verify` | CORS 扩展失效、基础头丢失、HTTP 映射/重试头回退 |
| 独立 JVM 会话测试 | `mvn -Predis-it verify` | 同环境不共享、不同项目/环境互认、踢人/退出越界、搜索越界 |
| GitHub Actions | 后端相关 push / PR | 自动执行以上测试、生产包隔离和演示编译检查 |

真实会话测试使用四个独立 JVM 和同一 Redis 数据库，验证同项目/环境的两个实例，以及不同环境、不同项目的实例。测试清理限于随机测试命名空间。

CI 工作流位于仓库 `.github/workflows/backend-base.yml`。若要在合并前强制执行，应在仓库 ruleset/分支保护中将 `backend-checks` 设为必需检查；这里只提交工作流，不改仓库合并策略。开发者仍需评审测试和规则本身的修改，自动化不能替代架构判断。

后续增加模块或模板时，复用上述配置和类型，扩展测试而非复制另一份实现。生成代码也应经过同一组架构与配置回归检查。
