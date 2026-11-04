# Backend Base 开发约束

修改会话、分布式公共组件、异常或 CORS 前，阅读 `docs/architecture-guardrails.md`。

- 应用/环境标识的唯一来源是 `ApplicationIdentity` 和 `app.identity`。会话及分布式 Key 复用它；禁止独立维护第二份 namespace/environment 或回退读取旧的无前缀会话。
- Sa-Token 只能通过受校验的 `NamespacedSaTokenDao` 持久化。存储适配包以外的应用代码通过 Sa-Token 公共 API 操作会话，不直接访问 DAO；禁止在应用代码中调用 `SaManager.setSaTokenDao`，或用未隔离的 DAO 替换它。
- `common` 不依赖具体功能、配置或适配器；分布式核心不依赖 HTTP、Servlet 或 Web 层。核心异常使用 `FailureCategory`、业务码和可选重试秒数，HTTP 映射位于 `web.error`。
- 业务自定义跨域头通过 `app.cors` 配置扩展。保留框架认证、防重、请求关联和重试所需的基础头；不要把业务专用头写死在 `WebConfig`。
- 代码改动执行相应 Maven 回归测试。会话隔离改动还须执行真实 Redis 的 `redis-it` 测试；只用 Mock 或同 JVM 多 Spring 上下文不能替代跨进程会话隔离验证。
- 不得以跳过/删除保护测试或扩大架构规则排除范围来掩盖问题。确需改变约束时，同步说明设计、迁移、规则和测试的变化。
- 更新 README、配置示例及生成器设计中的相关约定。文档改动执行链接和示例检查即可。

## 提交与 PR

遵循 [仓库 PR 规则](../AGENTS.md)：完成修改和相关验证后提交任务分支并创建 PR；独立问题分别提交，用户决定合并。默认不直接推送 `main`，也不自行合并或开启自动合并。

- PR 正文列出实际执行的 Maven profile/命令及结果，涉及行为变化时说明接入调整和兼容性。真实中间件测试、Mock 测试与未验证场景须区分。
- 按改动选择 `mvn verify`、`mvn -Predis-it verify`、`mvn -Pmessaging-it verify`；两个集成 profile 分别执行。演示改动还须检查 `distributed-demo` 构建及默认包隔离。
- 纯文档改动只检查链接、锚点、配置/API 示例和命令一致性，无需运行无关的 Maven 测试。README 和文档导航中的功能状态须与目标分支代码一致。
