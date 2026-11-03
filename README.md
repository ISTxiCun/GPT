# GPT 项目导航

## Backend Base：面向分布式应用的后端脚手架

**为分布式应用开发提供按需启用、开箱即用的基础能力。** 源码位于 **[`backend_base/`](backend_base/)**，通过统一配置、公共组件和双实例验收环境，让业务模块直接接入共享会话、分布式锁、接口限流和短窗口防重复提交。

采用模块化单体组织代码，支持同一应用多实例部署，并为后续按业务边界拆分服务保留清晰的协作约定。技术基线为 JDK 17、Spring Boot 3.5.9、MyBatis-Plus、MySQL、Redis、Redisson 和 Sa-Token。

当前 V1 已交付。账号体系和业务流程由项目定义，公共能力按需开启、组合使用；V2 缓存、V3 可靠异步处理、V4 运行与观测已记录开发计划，尚未实现。

| 需要做什么 | 文档入口 |
|---|---|
| 准备环境、启动应用、开发业务模块 | [后端 README](backend_base/README.md) |
| 开启并调用分布式公共能力 | [配置与调用示例](backend_base/README.md#11-分布式基础能力) |
| 验证跨实例登录、限流、防重与锁 | [双实例演示步骤](backend_base/README.md#12-双实例演示) |
| 查看算法、异常和适用边界 | [分布式能力详细说明](backend_base/docs/distributed.md) |
| 了解 V2～V4 的实现计划 | [开发路线图](backend_base/docs/distributed-roadmap.md) |

后端相关 Maven、Compose 命令均在 `backend_base` 目录执行；首次使用请从后端 README 的环境准备开始。

## 参考资料

- [ruoyi-vue-pro 模块导航](docs/ruoyi-vue-pro/README.md)
