# GPT 项目导航

## 后端脚手架

源码位于 **[`backend_base/`](backend_base/)**，采用 JDK 17、Spring Boot 3.5.9、MyBatis-Plus、MySQL、Redis 和 Sa-Token，单 Maven 模块、按业务包模块化。

已提供默认关闭、按需开启的分布式锁、接口限流和短窗口防重复提交能力。脚手架不包含具体账号、商品、订单等业务实现。

| 需要做什么 | 文档入口 |
|---|---|
| 准备环境、启动应用、开发业务模块 | [后端 README](backend_base/README.md) |
| 开启并调用分布式公共能力 | [配置与调用示例](backend_base/README.md#11-分布式基础能力) |
| 验证跨实例登录、限流、防重与锁 | [双实例演示步骤](backend_base/README.md#12-双实例演示) |
| 查看算法、异常和适用边界 | [分布式能力详细说明](backend_base/docs/distributed.md) |

后端相关 Maven、Compose 命令均在 `backend_base` 目录执行；首次使用请从后端 README 的环境准备开始。

## 参考资料

- [ruoyi-vue-pro 模块导航](docs/ruoyi-vue-pro/README.md)
