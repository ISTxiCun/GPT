# ruoyi-vue-pro 模块分析导航

本目录用于后续整理 ruoyi-vue-pro 的模块分析。目前仅建立文档命名与导航，各模块文档均为待分析占位，不包含功能、实现、依赖或裁剪分析。

## 识别依据

- 上游仓库：[YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro)。
- 核对日期：2026-11-03。
- 分支：`master`；核对时提交：[`42528bff6de57761430db057e628f7c5c979d676`](https://github.com/YunaiV/ruoyi-vue-pro/commit/42528bff6de57761430db057e628f7c5c979d676)。
- 根据根目录 `pom.xml`、实际模块目录和框架／商城／IoT 的子模块 `pom.xml` 建立清单，包含默认未启用的扩展模块。目录存在不代表默认参与构建。
- 本清单针对该分支快照；其他分支或版本需重新核对。`yudao-ui`、`sql`、`script` 等非后端 Maven 模块目录不在本次清单内。

## 命名约定

文档使用源码模块原名加 `.md`，如 `yudao-module-system.md`。子模块按源码层级存放，如 `yudao-framework/yudao-common.md`；父模块文档保留在本目录。所有文档当前状态均为“待分析”。

## 顶层模块

| 文档 | 上游源码目录 | 状态 |
| --- | --- | --- |
| [yudao-dependencies.md](yudao-dependencies.md) | [`yudao-dependencies`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-dependencies) | 待分析 |
| [yudao-framework.md](yudao-framework.md) | [`yudao-framework`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework) | 待分析 |
| [yudao-module-ai.md](yudao-module-ai.md) | [`yudao-module-ai`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-ai) | 待分析 |
| [yudao-module-ai1-springai.md](yudao-module-ai1-springai.md) | [`yudao-module-ai1-springai`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-ai1-springai) | 待分析 |
| [yudao-module-bpm.md](yudao-module-bpm.md) | [`yudao-module-bpm`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-bpm) | 待分析 |
| [yudao-module-crm.md](yudao-module-crm.md) | [`yudao-module-crm`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-crm) | 待分析 |
| [yudao-module-erp.md](yudao-module-erp.md) | [`yudao-module-erp`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-erp) | 待分析 |
| [yudao-module-fms.md](yudao-module-fms.md) | [`yudao-module-fms`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-fms) | 待分析 |
| [yudao-module-hrm.md](yudao-module-hrm.md) | [`yudao-module-hrm`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-hrm) | 待分析 |
| [yudao-module-im.md](yudao-module-im.md) | [`yudao-module-im`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-im) | 待分析 |
| [yudao-module-infra.md](yudao-module-infra.md) | [`yudao-module-infra`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-infra) | 待分析 |
| [yudao-module-iot.md](yudao-module-iot.md) | [`yudao-module-iot`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-iot) | 待分析 |
| [yudao-module-mall.md](yudao-module-mall.md) | [`yudao-module-mall`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mall) | 待分析 |
| [yudao-module-member.md](yudao-module-member.md) | [`yudao-module-member`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-member) | 待分析 |
| [yudao-module-mes.md](yudao-module-mes.md) | [`yudao-module-mes`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mes) | 待分析 |
| [yudao-module-mp.md](yudao-module-mp.md) | [`yudao-module-mp`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mp) | 待分析 |
| [yudao-module-oa.md](yudao-module-oa.md) | [`yudao-module-oa`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-oa) | 待分析 |
| [yudao-module-pay.md](yudao-module-pay.md) | [`yudao-module-pay`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-pay) | 待分析 |
| [yudao-module-pms.md](yudao-module-pms.md) | [`yudao-module-pms`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-pms) | 待分析 |
| [yudao-module-report.md](yudao-module-report.md) | [`yudao-module-report`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-report) | 待分析 |
| [yudao-module-system.md](yudao-module-system.md) | [`yudao-module-system`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-system) | 待分析 |
| [yudao-module-wms.md](yudao-module-wms.md) | [`yudao-module-wms`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-wms) | 待分析 |
| [yudao-server.md](yudao-server.md) | [`yudao-server`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-server) | 待分析 |

## yudao-framework 子模块

| 文档 | 上游源码目录 | 状态 |
| --- | --- | --- |
| [yudao-framework/yudao-common.md](yudao-framework/yudao-common.md) | [`yudao-framework/yudao-common`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-common) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-biz-data-permission.md](yudao-framework/yudao-spring-boot-starter-biz-data-permission.md) | [`yudao-framework/yudao-spring-boot-starter-biz-data-permission`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-biz-data-permission) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-biz-ip.md](yudao-framework/yudao-spring-boot-starter-biz-ip.md) | [`yudao-framework/yudao-spring-boot-starter-biz-ip`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-biz-ip) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-biz-tenant.md](yudao-framework/yudao-spring-boot-starter-biz-tenant.md) | [`yudao-framework/yudao-spring-boot-starter-biz-tenant`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-biz-tenant) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-excel.md](yudao-framework/yudao-spring-boot-starter-excel.md) | [`yudao-framework/yudao-spring-boot-starter-excel`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-excel) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-job.md](yudao-framework/yudao-spring-boot-starter-job.md) | [`yudao-framework/yudao-spring-boot-starter-job`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-job) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-monitor.md](yudao-framework/yudao-spring-boot-starter-monitor.md) | [`yudao-framework/yudao-spring-boot-starter-monitor`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-monitor) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-mq.md](yudao-framework/yudao-spring-boot-starter-mq.md) | [`yudao-framework/yudao-spring-boot-starter-mq`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-mq) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-mybatis.md](yudao-framework/yudao-spring-boot-starter-mybatis.md) | [`yudao-framework/yudao-spring-boot-starter-mybatis`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-mybatis) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-protection.md](yudao-framework/yudao-spring-boot-starter-protection.md) | [`yudao-framework/yudao-spring-boot-starter-protection`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-protection) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-redis.md](yudao-framework/yudao-spring-boot-starter-redis.md) | [`yudao-framework/yudao-spring-boot-starter-redis`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-redis) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-security.md](yudao-framework/yudao-spring-boot-starter-security.md) | [`yudao-framework/yudao-spring-boot-starter-security`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-security) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-test.md](yudao-framework/yudao-spring-boot-starter-test.md) | [`yudao-framework/yudao-spring-boot-starter-test`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-test) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-web.md](yudao-framework/yudao-spring-boot-starter-web.md) | [`yudao-framework/yudao-spring-boot-starter-web`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-web) | 待分析 |
| [yudao-framework/yudao-spring-boot-starter-websocket.md](yudao-framework/yudao-spring-boot-starter-websocket.md) | [`yudao-framework/yudao-spring-boot-starter-websocket`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-framework/yudao-spring-boot-starter-websocket) | 待分析 |

## yudao-module-mall 子模块

| 文档 | 上游源码目录 | 状态 |
| --- | --- | --- |
| [yudao-module-mall/yudao-module-product.md](yudao-module-mall/yudao-module-product.md) | [`yudao-module-mall/yudao-module-product`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mall/yudao-module-product) | 待分析 |
| [yudao-module-mall/yudao-module-promotion.md](yudao-module-mall/yudao-module-promotion.md) | [`yudao-module-mall/yudao-module-promotion`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mall/yudao-module-promotion) | 待分析 |
| [yudao-module-mall/yudao-module-statistics.md](yudao-module-mall/yudao-module-statistics.md) | [`yudao-module-mall/yudao-module-statistics`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mall/yudao-module-statistics) | 待分析 |
| [yudao-module-mall/yudao-module-trade-api.md](yudao-module-mall/yudao-module-trade-api.md) | [`yudao-module-mall/yudao-module-trade-api`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mall/yudao-module-trade-api) | 待分析 |
| [yudao-module-mall/yudao-module-trade.md](yudao-module-mall/yudao-module-trade.md) | [`yudao-module-mall/yudao-module-trade`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-mall/yudao-module-trade) | 待分析 |

## yudao-module-iot 子模块

| 文档 | 上游源码目录 | 状态 |
| --- | --- | --- |
| [yudao-module-iot/yudao-module-iot-biz.md](yudao-module-iot/yudao-module-iot-biz.md) | [`yudao-module-iot/yudao-module-iot-biz`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-iot/yudao-module-iot-biz) | 待分析 |
| [yudao-module-iot/yudao-module-iot-core.md](yudao-module-iot/yudao-module-iot-core.md) | [`yudao-module-iot/yudao-module-iot-core`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-iot/yudao-module-iot-core) | 待分析 |
| [yudao-module-iot/yudao-module-iot-gateway.md](yudao-module-iot/yudao-module-iot-gateway.md) | [`yudao-module-iot/yudao-module-iot-gateway`](https://github.com/YunaiV/ruoyi-vue-pro/tree/42528bff6de57761430db057e628f7c5c979d676/yudao-module-iot/yudao-module-iot-gateway) | 待分析 |
