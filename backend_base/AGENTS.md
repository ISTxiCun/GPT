# Backend Base 开发约束

修改会话、分布式公共组件、异常或 CORS 前，阅读 `docs/architecture-guardrails.md`。

- 应用/环境标识的唯一来源是 `ApplicationIdentity` 和 `app.identity`。会话及分布式 Key 复用它；禁止独立维护第二份 namespace/environment 或回退读取旧的无前缀会话。
- Sa-Token 只能通过受校验的 `NamespacedSaTokenDao` 持久化。存储适配包以外的应用代码通过 Sa-Token 公共 API 操作会话，不直接访问 DAO；禁止在应用代码中调用 `SaManager.setSaTokenDao`，或用未隔离的 DAO 替换它。
- `common` 不依赖具体功能、配置或适配器；分布式核心不依赖 HTTP、Servlet 或 Web 层。核心异常使用 `FailureCategory`、业务码和可选重试秒数，HTTP 映射位于 `web.error`。
- 业务自定义跨域头通过 `app.cors` 配置扩展。保留框架认证、防重、请求关联和重试所需的基础头；不要把业务专用头写死在 `WebConfig`。
- 代码改动执行相应 Maven 回归测试。会话隔离改动还须执行真实 Redis 的 `redis-it` 测试；只用 Mock 或同 JVM 多 Spring 上下文不能替代跨进程会话隔离验证。
- 不得以跳过/删除保护测试或扩大架构规则排除范围来掩盖问题。确需改变约束时，同步说明设计、迁移、规则和测试的变化。
- 更新 README、配置示例及生成器设计中的相关约定。文档改动执行链接和示例检查即可。
