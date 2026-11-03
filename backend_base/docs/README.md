# 脚手架文档导航

本目录属于 `backend_base` 脚手架；仓库根目录 `docs/` 中的其他项目分析资料独立维护。

| 文档 | 用途 | 状态 |
|---|---|---|
| [项目 README](../README.md) | 环境、启动、业务接入、演示和排错 | 当前使用说明 |
| [快速生成模块](module-generator.md) | 模块生成能力的需求、待讨论项与设计记录 | 需求收集中，未实现 |
| [分布式基础能力 V1](distributed.md) | 锁、限流、短窗口防重复提交的实际用法 | 已实现 |
| [分布式开发路线图](distributed-roadmap.md) | V2～V4 范围、顺序、共同约束和开发检查表 | 规划 |
| [V2：缓存基础](distributed-v2-cache.md) | 缓存入口、TTL、空值缓存、热点加载保护 | 未实现 |
| [V3：可靠异步处理](distributed-v3-messaging.md) | RabbitMQ、消息契约、重试、消费幂等和 Outbox | 未实现 |
| [V4：运行与观测](distributed-v4-observability.md) | 指标、链路、健康检查、停机和故障验证 | 未实现 |

V2～V4 文档中的接口、配置、表名和交付项都是开发提案，当前代码尚不支持，不可直接作为使用手册执行。
