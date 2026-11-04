# GPT 项目导航

## Backend Base：面向分布式应用的后端脚手架

**为分布式应用开发提供按需启用、开箱即用的基础能力。** 源码位于 **[`backend_base/`](backend_base/)**，通过统一配置、公共组件和双实例验收环境，让业务模块直接接入共享会话、分布式锁、接口限流、短窗口防重复提交、显式缓存与可靠异步消息。

采用模块化单体组织代码，支持同一应用多实例部署，并为后续按业务边界拆分服务保留清晰的协作约定。技术基线为 JDK 17、Spring Boot 3.5.9、MyBatis-Plus、MySQL、Redis、Redisson 和 Sa-Token；V3 消息适配使用 RabbitMQ 4.1 与 Spring AMQP。

当前 V1～V3 已实现，包括类型明确的缓存、RabbitMQ 消息、MySQL 消费幂等和可选 Outbox。缓存与消息功能默认关闭，JDBC 消息表需要显式迁移；缓存不保证强一致，消息按至少一次投递设计。账号体系、权限和业务流程由项目定义。V4 运行观测及模块生成器仍为规划。

| 需要做什么 | 文档入口 |
|---|---|
| 准备环境、启动应用、开发业务模块 | [后端 README](backend_base/README.md) |
| 开启并调用分布式公共能力 | [配置与调用示例](backend_base/README.md#11-分布式基础能力) |
| 验证跨实例登录、限流、防重与锁 | [双实例演示步骤](backend_base/README.md#12-双实例演示) |
| 查看算法、异常和适用边界 | [分布式能力详细说明](backend_base/docs/distributed.md) |
| 接入 V2 缓存及了解事务、降级边界 | [缓存使用说明](backend_base/docs/distributed-v2-cache.md) |
| 接入 V3 消息、消费幂等与 Outbox | [消息使用说明](backend_base/docs/distributed-v3-messaging.md) |
| 运行单元、Redis 和消息集成验收 | [测试与打包](backend_base/README.md#9-测试与打包) |
| 查看已交付能力与后续计划 | [开发路线图](backend_base/docs/distributed-roadmap.md) |
| 修改代码、验证并提交 PR | [仓库协作规则](AGENTS.md) |

后端相关 Maven、Compose 命令均在 `backend_base` 目录执行；首次使用请从后端 README 的环境准备开始。

## 提交改动

代码和文档改动均通过独立分支提交 PR，说明问题、修改、验证及兼容性影响。多个独立问题分别建 PR，默认不堆叠依赖；由维护者决定合并，不直接推送 `main` 或自动合并。完整规则见 [AGENTS.md](AGENTS.md)，后端另遵循 [backend_base/AGENTS.md](backend_base/AGENTS.md)。

## 参考资料

- [ruoyi-vue-pro 模块导航](docs/ruoyi-vue-pro/README.md)
