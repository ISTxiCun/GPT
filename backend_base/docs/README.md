# 脚手架文档导航

本目录属于 `backend_base` 脚手架；仓库根目录 `docs/` 中的其他项目分析资料独立维护。

| 文档 | 用途 | 状态 |
|---|---|---|
| [项目 README](../README.md) | 环境、启动、业务接入、演示和排错 | 当前使用说明 |
| [快速生成模块](module-generator.md) | 18 类模块、功能勾选、依赖、生成流程和验收设计 | 设计规划，未实现 |
| [架构保护与配置迁移](architecture-guardrails.md) | 统一环境标识、错误分层、CORS 扩展和回归规则 | 已实现 |
| [分布式基础能力 V1](distributed.md) | 锁、限流、短窗口防重复提交的实际用法 | 已实现 |
| [分布式开发路线图](distributed-roadmap.md) | V1～V3 交付状态、V4 后续安排、共同约束和开发检查表 | 实施状态与规划 |
| [V2：缓存基础](distributed-v2-cache.md) | 缓存入口、TTL、空值缓存、热点加载保护、事务与降级限制 | 已实现 |
| [V3：可靠异步处理](distributed-v3-messaging.md) | RabbitMQ、消息契约、重试、消费幂等和 Outbox 续租 | 已实现 |
| [V4：运行与观测](distributed-v4-observability.md) | 指标、链路、健康检查、停机和故障验证 | 未实现 |

V2/V3 的最终 API、配置、故障和迁移约定见各文档第 7 节；前面的规划段落保留设计依据。V4 及模块生成器仍为规划，不能当作现有功能使用。

代码和文档改动遵循 [仓库 PR 规则](../../AGENTS.md) 与 [后端开发约束](../AGENTS.md)。
