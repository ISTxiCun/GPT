# yudao-cloud 模块用途与分析导航

这里先说明每个模块“负责什么”，再提供对应分析文档入口。共收录 **23 个顶层模块、63 个子模块**，合计 **86 份模块文档**。各模块详细分析文档目前仍为“待分析”。

## 先看整体结构

yudao-cloud 是基于 Spring Cloud Alibaba 的微服务后端。业务模块通常拆成 `api` 与 `server`：前者供其他后端模块引用调用契约，后者承载业务实现。`yudao-gateway` 是统一 API 网关，`yudao-framework` 提供技术组件，`yudao-dependencies` 管理依赖版本。

- 想了解后台账号、部门和权限：看 **system**。
- 想找配置、文件、代码生成等管理与研发工具：看 **infra**。
- 想了解请求入口与服务间调用：看 **gateway** 和 **rpc** 组件。
- 想了解具体业务：按下方中文名称进入模块；业务实现优先看对应 **server**，跨服务调用契约看 **api**。
- 仓库也保留 `yudao-server` 组合启动入口；其当前 POM 默认引入 system-server 与 infra-server，其他业务依赖按需启用。

**阅读说明：**用途依据本仓库的 README 和各模块 `pom.xml` 整理，用于理解模块定位；声明范围不代表每项功能均已实现或验证可用。根 POM 中除 AI 外，下面列出的顶层模块均纳入构建；AI 在该 JDK 8 分支默认注释，上游提示需使用 JDK 17 版本。**参与 Maven 构建、被组合入口引入、实际部署运行是不同概念**，不能据此认定所有服务已启用。

## 基础模块：依赖、网关、框架与启动

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-dependencies`](yudao-dependencies.md) | 依赖版本管理 | 通过 BOM 统一管理项目组件与第三方依赖版本，避免各模块版本不一致。 |
| [`yudao-framework`](yudao-framework.md) | 公共技术框架 | 聚合数据库、缓存、认证、Web、RPC 等可复用技术组件，支撑各业务服务。 |
| [`yudao-gateway`](yudao-gateway.md) | 统一 API 网关 | 基于 Spring Cloud Gateway 提供统一 API 入口，将请求路由到相应后端服务。 |
| [`yudao-server`](yudao-server.md) | 后端组合启动入口 | 通过引入业务 server 模块组装后端；当前 POM 默认引入 system-server 与 infra-server，其他业务按需引入。 |

## 顶层业务模块：按业务找到入口

顶层业务模块多为 Maven 聚合入口，不应将每个目录都理解成一个独立运行进程。具体 api 与 server 文档见后续分组。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-ai`](yudao-module-ai.md) | AI 大模型应用 | 接入大模型，提供聊天、绘图、音乐、写作、思维导图等 AI 应用能力。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-bpm`](yudao-module-bpm.md) | 工作流与审批 | 基于 Flowable 管理流程定义、表单、流程实例与审批任务，例如我的申请、待办和已办。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-crm`](yudao-module-crm.md) | 客户关系管理（CRM） | 管理客户、联系人、商机、合同及回款等销售业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-erp`](yudao-module-erp.md) | 企业资源计划（ERP） | 管理采购、销售、库存、产品及财务等企业经营业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-fms`](yudao-module-fms.md) | 财务管理（FMS） | 面向科目、凭证、结账和财务报表等财务业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-hrm`](yudao-module-hrm.md) | 人力资源管理（HRM） | 面向员工、考勤、招聘、绩效和薪酬等人事业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-im`](yudao-module-im.md) | 即时通讯（IM） | 面向单聊、群聊、消息收发、撤回和已读状态等聊天业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-infra`](yudao-module-infra.md) | 基础设施与研发工具 | 提供基础设施运维管理和研发工具，例如配置、文件、代码生成与接口文档。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-iot`](yudao-module-iot.md) | 物联网（IoT） | 管理产品、物模型、设备、规则引擎、OTA 升级及设备接入。 |
| [`yudao-module-mall`](yudao-module-mall.md) | 电子商城 | 聚合商品、营销、交易和统计模块，组织电子商城业务。 |
| [`yudao-module-member`](yudao-module-member.md) | 会员中心 | 管理业务用户的会员信息与会员中心；与后台管理员账号用途不同。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-mes`](yudao-module-mes.md) | 制造执行（MES） | 面向生产现场的基础数据、排班、设备、工具、生产和质量等业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-mp`](yudao-module-mp.md) | 微信公众号 | 管理公众号账号、菜单、粉丝、消息、自动回复、素材和模板通知。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-oa`](yudao-module-oa.md) | 办公自动化（OA） | 面向日程、云盘、请假报销、会议室、车辆、印章和公文等协同办公业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-pay`](yudao-module-pay.md) | 支付系统 | 管理商户、支付应用、支付和退款，为其他业务提供支付能力。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-pms`](yudao-module-pms.md) | 项目管理（PMS） | 管理项目、工作事项、迭代、看板、工时和知识库等项目协作业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-report`](yudao-module-report.md) | 数据报表 | 提供数据可视化报表、打印设计、报表设计、预览和导出等能力。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-system`](yudao-module-system.md) | 系统管理 | 管理后台用户、部门、角色权限、数据字典等通用系统业务。作为聚合入口组织对应 api 与 server 子模块。 |
| [`yudao-module-wms`](yudao-module-wms.md) | 仓库管理（WMS） | 管理仓库、物料、库存及入库、出库、移库和盘库等仓储作业。作为聚合入口组织对应 api 与 server 子模块。 |

## 框架组件：公共技术能力

`starter` 用于接入并配置相应技术能力；`biz` 表示带有业务语义的公共组件。这些通常由后端服务引用，不对应独立业务页面。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-common`](yudao-framework/yudao-common.md) | 公共基础类型与工具 | 提供多个模块共用的基础对象、枚举和工具类。 |
| [`yudao-spring-boot-starter-biz-data-permission`](yudao-framework/yudao-spring-boot-starter-biz-data-permission.md) | 数据权限 | 封装数据访问范围控制，例如让用户只查询有权查看的数据。 |
| [`yudao-spring-boot-starter-biz-ip`](yudao-framework/yudao-spring-boot-starter-biz-ip.md) | IP 与行政区划 | 查询 IP 对应城市，以及行政区划编码对应地区的信息。 |
| [`yudao-spring-boot-starter-biz-tenant`](yudao-framework/yudao-spring-boot-starter-biz-tenant.md) | 多租户 | 为不同租户提供底层多租户支持。 |
| [`yudao-spring-boot-starter-env`](yudao-framework/yudao-spring-boot-starter-env.md) | 开发环境扩展 | 提供类似特性环境的支持，辅助微服务开发环境的隔离与协作。 |
| [`yudao-spring-boot-starter-excel`](yudao-framework/yudao-spring-boot-starter-excel.md) | Excel 导入导出 | 封装 Excel 表格处理能力，供业务模块导入与导出数据。 |
| [`yudao-spring-boot-starter-job`](yudao-framework/yudao-spring-boot-starter-job.md) | 分布式任务 | 基于 XXL-Job 封装任务执行能力；本分支使用 XXL-Job，与单体版的 Quartz 组件有区别。 |
| [`yudao-spring-boot-starter-monitor`](yudao-framework/yudao-spring-boot-starter-monitor.md) | 监控与链路追踪 | 提供链路追踪、日志服务和指标收集等服务观测能力。 |
| [`yudao-spring-boot-starter-mq`](yudao-framework/yudao-spring-boot-starter-mq.md) | 消息队列 | 封装消息队列能力，支持 Redis、RocketMQ、RabbitMQ、Kafka。 |
| [`yudao-spring-boot-starter-mybatis`](yudao-framework/yudao-spring-boot-starter-mybatis.md) | 数据库访问 | 封装连接池、多数据源、事务及 MyBatis 扩展，支撑业务数据读写。 |
| [`yudao-spring-boot-starter-protection`](yudao-framework/yudao-spring-boot-starter-protection.md) | 服务保护 | 封装分布式锁、幂等、限流等服务保护能力。 |
| [`yudao-spring-boot-starter-redis`](yudao-framework/yudao-spring-boot-starter-redis.md) | Redis 缓存 | 封装 Redis 的配置和访问扩展，供业务服务使用缓存等能力。 |
| [`yudao-spring-boot-starter-rpc`](yudao-framework/yudao-spring-boot-starter-rpc.md) | 服务间远程调用 | 基于 OpenFeign 封装 REST API 调用，供后端服务之间通信。 |
| [`yudao-spring-boot-starter-security`](yudao-framework/yudao-spring-boot-starter-security.md) | 认证与权限校验 | 封装用户身份认证、权限校验及操作日志等安全基础能力。 |
| [`yudao-spring-boot-starter-test`](yudao-framework/yudao-spring-boot-starter-test.md) | 测试支持 | 为单元测试与集成测试提供公共组件。 |
| [`yudao-spring-boot-starter-web`](yudao-framework/yudao-spring-boot-starter-web.md) | Web 接口基础能力 | 封装全局异常处理、API 日志、脱敏和错误码等通用 Web 能力。 |
| [`yudao-spring-boot-starter-websocket`](yudao-framework/yudao-spring-boot-starter-websocket.md) | WebSocket 实时通信 | 封装 WebSocket 长连接通信，支持多节点广播。 |

## 常规业务子模块：API 契约与服务实现

每组都保留 api 与 server 两份分析文档，便于后续分别梳理远程调用契约和业务实现。这里的 api 指后端模块间调用契约，不代表前端页面，也不代表需要单独启动一个 API 服务。

### AI 大模型应用（yudao-module-ai）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-ai-api`](yudao-module-ai/yudao-module-ai-api.md) | AI 大模型应用对外接口 | 向其他后端模块暴露AI 大模型应用的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-ai-server`](yudao-module-ai/yudao-module-ai-server.md) | AI 大模型应用业务服务 | 承载AI 大模型应用的服务端实现。接入大模型，提供聊天、绘图、音乐、写作、思维导图等 AI 应用能力。 |

### 工作流与审批（yudao-module-bpm）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-bpm-api`](yudao-module-bpm/yudao-module-bpm-api.md) | 工作流与审批对外接口 | 向其他后端模块暴露工作流与审批的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-bpm-server`](yudao-module-bpm/yudao-module-bpm-server.md) | 工作流与审批业务服务 | 承载工作流与审批的服务端实现。基于 Flowable 管理流程定义、表单、流程实例与审批任务，例如我的申请、待办和已办。 |

### 客户关系管理（CRM）（yudao-module-crm）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-crm-api`](yudao-module-crm/yudao-module-crm-api.md) | 客户关系管理（CRM）对外接口 | 向其他后端模块暴露客户关系管理（CRM）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-crm-server`](yudao-module-crm/yudao-module-crm-server.md) | 客户关系管理（CRM）业务服务 | 承载客户关系管理（CRM）的服务端实现。管理客户、联系人、商机、合同及回款等销售业务。 |

### 企业资源计划（ERP）（yudao-module-erp）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-erp-api`](yudao-module-erp/yudao-module-erp-api.md) | 企业资源计划（ERP）对外接口 | 向其他后端模块暴露企业资源计划（ERP）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-erp-server`](yudao-module-erp/yudao-module-erp-server.md) | 企业资源计划（ERP）业务服务 | 承载企业资源计划（ERP）的服务端实现。管理采购、销售、库存、产品及财务等企业经营业务。 |

### 财务管理（FMS）（yudao-module-fms）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-fms-api`](yudao-module-fms/yudao-module-fms-api.md) | 财务管理（FMS）对外接口 | 向其他后端模块暴露财务管理（FMS）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-fms-server`](yudao-module-fms/yudao-module-fms-server.md) | 财务管理（FMS）业务服务 | 承载财务管理（FMS）的服务端实现。面向科目、凭证、结账和财务报表等财务业务。 |

### 人力资源管理（HRM）（yudao-module-hrm）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-hrm-api`](yudao-module-hrm/yudao-module-hrm-api.md) | 人力资源管理（HRM）对外接口 | 向其他后端模块暴露人力资源管理（HRM）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-hrm-server`](yudao-module-hrm/yudao-module-hrm-server.md) | 人力资源管理（HRM）业务服务 | 承载人力资源管理（HRM）的服务端实现。面向员工、考勤、招聘、绩效和薪酬等人事业务。 |

### 即时通讯（IM）（yudao-module-im）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-im-api`](yudao-module-im/yudao-module-im-api.md) | 即时通讯（IM）对外接口 | 向其他后端模块暴露即时通讯（IM）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-im-server`](yudao-module-im/yudao-module-im-server.md) | 即时通讯（IM）业务服务 | 承载即时通讯（IM）的服务端实现。面向单聊、群聊、消息收发、撤回和已读状态等聊天业务。 |

### 基础设施与研发工具（yudao-module-infra）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-infra-api`](yudao-module-infra/yudao-module-infra-api.md) | 基础设施与研发工具对外接口 | 向其他后端模块暴露基础设施与研发工具的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-infra-server`](yudao-module-infra/yudao-module-infra-server.md) | 基础设施与研发工具业务服务 | 承载基础设施与研发工具的服务端实现。提供基础设施运维管理和研发工具，例如配置、文件、代码生成与接口文档。 |

### 会员中心（yudao-module-member）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-member-api`](yudao-module-member/yudao-module-member-api.md) | 会员中心对外接口 | 向其他后端模块暴露会员中心的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-member-server`](yudao-module-member/yudao-module-member-server.md) | 会员中心业务服务 | 承载会员中心的服务端实现。管理业务用户的会员信息与会员中心；与后台管理员账号用途不同。 |

### 制造执行（MES）（yudao-module-mes）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-mes-api`](yudao-module-mes/yudao-module-mes-api.md) | 制造执行（MES）对外接口 | 向其他后端模块暴露制造执行（MES）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-mes-server`](yudao-module-mes/yudao-module-mes-server.md) | 制造执行（MES）业务服务 | 承载制造执行（MES）的服务端实现。面向生产现场的基础数据、排班、设备、工具、生产和质量等业务。 |

### 微信公众号（yudao-module-mp）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-mp-api`](yudao-module-mp/yudao-module-mp-api.md) | 微信公众号对外接口 | 向其他后端模块暴露微信公众号的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-mp-server`](yudao-module-mp/yudao-module-mp-server.md) | 微信公众号业务服务 | 承载微信公众号的服务端实现。管理公众号账号、菜单、粉丝、消息、自动回复、素材和模板通知。 |

### 办公自动化（OA）（yudao-module-oa）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-oa-api`](yudao-module-oa/yudao-module-oa-api.md) | 办公自动化（OA）对外接口 | 向其他后端模块暴露办公自动化（OA）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-oa-server`](yudao-module-oa/yudao-module-oa-server.md) | 办公自动化（OA）业务服务 | 承载办公自动化（OA）的服务端实现。面向日程、云盘、请假报销、会议室、车辆、印章和公文等协同办公业务。 |

### 支付系统（yudao-module-pay）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-pay-api`](yudao-module-pay/yudao-module-pay-api.md) | 支付系统对外接口 | 向其他后端模块暴露支付系统的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-pay-server`](yudao-module-pay/yudao-module-pay-server.md) | 支付系统业务服务 | 承载支付系统的服务端实现。管理商户、支付应用、支付和退款，为其他业务提供支付能力。 |

### 项目管理（PMS）（yudao-module-pms）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-pms-api`](yudao-module-pms/yudao-module-pms-api.md) | 项目管理（PMS）对外接口 | 向其他后端模块暴露项目管理（PMS）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-pms-server`](yudao-module-pms/yudao-module-pms-server.md) | 项目管理（PMS）业务服务 | 承载项目管理（PMS）的服务端实现。管理项目、工作事项、迭代、看板、工时和知识库等项目协作业务。 |

### 数据报表（yudao-module-report）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-report-api`](yudao-module-report/yudao-module-report-api.md) | 数据报表对外接口 | 向其他后端模块暴露数据报表的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-report-server`](yudao-module-report/yudao-module-report-server.md) | 数据报表业务服务 | 承载数据报表的服务端实现。提供数据可视化报表、打印设计、报表设计、预览和导出等能力。 |

### 系统管理（yudao-module-system）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-system-api`](yudao-module-system/yudao-module-system-api.md) | 系统管理对外接口 | 向其他后端模块暴露系统管理的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-system-server`](yudao-module-system/yudao-module-system-server.md) | 系统管理业务服务 | 承载系统管理的服务端实现。管理后台用户、部门、角色权限、数据字典等通用系统业务。 |

### 仓库管理（WMS）（yudao-module-wms）

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-wms-api`](yudao-module-wms/yudao-module-wms-api.md) | 仓库管理（WMS）对外接口 | 向其他后端模块暴露仓库管理（WMS）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-wms-server`](yudao-module-wms/yudao-module-wms-server.md) | 仓库管理（WMS）业务服务 | 承载仓库管理（WMS）的服务端实现。管理仓库、物料、库存及入库、出库、移库和盘库等仓储作业。 |

## 商城子模块：商品、营销、交易与统计

商城的四个业务领域各自拆为 api 与 server，共八个子模块。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-product-api`](yudao-module-mall/yudao-module-product-api.md) | 商品管理对外接口 | 向其他后端模块暴露商品管理的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-product-server`](yudao-module-mall/yudao-module-product-server.md) | 商品管理业务服务 | 承载商品管理的服务端实现。管理品牌、商品分类、SPU（商品款式）和 SKU（具体规格商品）。 |
| [`yudao-module-promotion-api`](yudao-module-mall/yudao-module-promotion-api.md) | 营销管理对外接口 | 向其他后端模块暴露营销管理的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-promotion-server`](yudao-module-mall/yudao-module-promotion-server.md) | 营销管理业务服务 | 承载营销管理的服务端实现。管理营销活动、广告、优惠券和优惠码等促销业务。 |
| [`yudao-module-statistics-api`](yudao-module-mall/yudao-module-statistics-api.md) | 商城统计对外接口 | 向其他后端模块暴露商城统计的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-statistics-server`](yudao-module-mall/yudao-module-statistics-server.md) | 商城统计业务服务 | 承载商城统计的服务端实现。统计商品、会员和交易等商城经营数据。 |
| [`yudao-module-trade-api`](yudao-module-mall/yudao-module-trade-api.md) | 交易管理对外接口 | 向其他后端模块暴露交易管理的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-trade-server`](yudao-module-mall/yudao-module-trade-server.md) | 交易管理业务服务 | 承载交易管理的服务端实现。处理购物车、订单和退款等交易业务；支付能力由 pay 提供。 |

## 物联网子模块：接口、业务、核心与设备接入

物联网业务实现位于 iot-server；设备通信由 iot-gateway 处理，两者共用 iot-core，跨模块契约由 iot-api 提供。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-iot-api`](yudao-module-iot/yudao-module-iot-api.md) | 物联网（IoT）对外接口 | 向其他后端模块暴露物联网（IoT）的调用契约与共享数据类型，具体业务由对应 server 实现。 |
| [`yudao-module-iot-core`](yudao-module-iot/yudao-module-iot-core.md) | 物联网公共核心 | 提供 iot-server 与 iot-gateway 共用的设备消息总线、设备查询 API 和解析脚本执行能力。 |
| [`yudao-module-iot-gateway`](yudao-module-iot/yudao-module-iot-gateway.md) | 物联网设备网关 | 解码设备上报消息并送入消息总线，将平台消息编码后下发设备，适配 HTTP、MQTT、TCP 等接入协议。 |
| [`yudao-module-iot-server`](yudao-module-iot/yudao-module-iot-server.md) | 物联网业务服务 | 处理产品与物模型、设备、OTA、规则引擎、告警、统计，以及平台侧的设备消息业务。 |

## 容易混淆的模块与版本差异

| 模块组合 | 职责区别 |
| --- | --- |
| 顶层业务模块、api、server | 顶层通常组织子模块；api 提供调用契约；server 承载业务实现。目录数量不等于部署进程数量。 |
| yudao-gateway 与 iot-gateway | 前者面向平台 API 请求路由；后者面向物联网设备协议接入和消息编解码。 |
| yudao-server 与各业务 server | 前者通过依赖组合业务模块；后者承载具体业务。实际启动与部署方式需按项目配置确认。 |
| system 与 member | system 面向后台操作者及权限；member 面向业务用户的会员中心。 |
| system 与 security 组件 | system 提供账号、角色等管理业务；security 提供认证和权限校验机制。 |
| infra 与 framework | infra 提供运维管理与研发工具业务；framework 提供各服务复用的底层组件。 |
| trade 与 pay | trade 管理订单等交易流程；pay 提供支付与退款能力。 |
| WebSocket 与 IM | WebSocket 是实时通信基础组件；IM 是聊天、消息状态等具体业务。 |
| ERP 与 WMS、MES、FMS | ERP 面向整体经营，WMS 聚焦仓储，MES 聚焦生产，FMS 聚焦财务；定位相近不代表已经互相集成。 |
| cloud 与单体版的 job | 本 cloud 快照基于 XXL-Job；之前 ruoyi-vue-pro 快照中的 job 基于 Quartz 与 Spring Async。 |
| cloud 与单体版的 IoT | 本 cloud 快照使用 iot-api、iot-server、iot-core、iot-gateway；此前单体版业务模块名为 iot-biz。 |
| cloud 与单体版的框架组件 | 本 cloud 快照额外包含 env 开发环境组件和 rpc 远程调用组件，不能直接照搬单体版清单。 |

## 源码依据与文档约定

- 上游：[YunaiV/yudao-cloud](https://github.com/YunaiV/yudao-cloud)。
- 核对日期：2026-11-03；分支：`master`（JDK 8 / Spring Boot 2.7）；提交：[`608ec9e307d426e583047e60d336c5c3a9f822b3`](https://github.com/YunaiV/yudao-cloud/commit/608ec9e307d426e583047e60d336c5c3a9f822b3)。
- 用途依据：[上游 README](https://github.com/YunaiV/yudao-cloud/blob/608ec9e307d426e583047e60d336c5c3a9f822b3/README.md)、[根 pom.xml](https://github.com/YunaiV/yudao-cloud/blob/608ec9e307d426e583047e60d336c5c3a9f822b3/pom.xml) 及全部 86 个模块的 `pom.xml`。
- 默认组合范围依据：[yudao-server/pom.xml](https://github.com/YunaiV/yudao-cloud/blob/608ec9e307d426e583047e60d336c5c3a9f822b3/yudao-server/pom.xml)。
- 查找源码：文档相对路径去掉 `.md` 即上游模块目录；在该目录的 `pom.xml` 中可查看原始模块说明。
- 文档采用源码模块原名加 `.md`，子模块按源码层级存放。当前各模块文档仅包含标题与“待分析”；实现原理、依赖关系、数据库设计和裁剪方案留待后续补充。
- 清单针对该分支快照；其他分支或版本需重新核对。`yudao-ui`、`sql`、`script` 等非后端 Maven 模块目录不在本次清单内。
- 单体版导航：[ruoyi-vue-pro 模块用途与分析导航](../ruoyi-vue-pro/README.md)。
