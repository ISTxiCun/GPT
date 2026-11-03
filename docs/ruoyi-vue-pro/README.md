# ruoyi-vue-pro 模块用途与分析导航

这里先说明每个模块“负责什么”，再提供对应分析文档的入口。共收录 **23 个顶层模块、23 个子模块**。各模块的详细分析文档目前仍为“待分析”。

## 先看整体结构

后端由 `yudao-server` 启动并组装业务模块；`yudao-module-*` 承载系统管理、商城、支付等具体业务；`yudao-framework` 提供数据库、缓存、认证等公共技术能力；`yudao-dependencies` 统一管理依赖版本。

- 想了解后台账号、角色、菜单和权限管理：先看 **system**。
- 想找代码生成、文件、配置、任务管理：先看 **infra**。
- 想了解底层认证、数据库、缓存怎么封装：看 **framework** 的对应组件。
- 想了解具体业务：按下方中文名称找到模块，点击模块名进入预留的分析文档。

**阅读说明：**下表用途依据上游 README 和各模块 `pom.xml` 的描述整理，用于理解模块定位。它不是功能完整性或可用性验证；尤其是业务模块，声明的业务范围不代表每项功能都已实现。根 `pom.xml` 默认纳入构建的是 dependencies、framework、server、system、infra，其他顶层业务模块在该快照中默认被注释，需要按需启用并配置。

## 基础模块：启动与通用管理

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-dependencies`](yudao-dependencies.md) | 依赖版本管理 | 统一管理第三方库和项目组件的版本（BOM），避免各模块依赖版本不一致。 |
| [`yudao-framework`](yudao-framework.md) | 公共技术框架 | 汇总数据库、缓存、认证、Web 等可复用组件，为业务模块提供底层能力。 |
| [`yudao-server`](yudao-server.md) | 后端启动入口 | 组装需要启用的业务模块，启动后端服务，向前端提供 REST API。 |
| [`yudao-module-system`](yudao-module-system.md) | 系统管理 | 管理后台账号、角色、菜单权限、部门、字典、租户，以及短信、邮件等通用系统功能。 |
| [`yudao-module-infra`](yudao-module-infra.md) | 基础设施与研发工具 | 提供代码生成、配置管理、文件管理、定时任务管理和 API 日志等能力。 |

## 按业务选用的模块

这些模块面向不同业务场景，可按项目需要选择。英文缩写对应的中文用途如下。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-ai`](yudao-module-ai.md) | AI 大模型应用 | 接入大模型，提供聊天、绘图、音乐、写作、思维导图等 AI 应用能力。 |
| [`yudao-module-ai1-springai`](yudao-module-ai1-springai.md) | AI Agent 编排（Spring AI） | 侧重组合模型、系统指令、知识库、MCP 和 SKILL 构建 Agent，并提供流式对话能力。 |
| [`yudao-module-bpm`](yudao-module-bpm.md) | 工作流与审批 | 基于 Flowable 管理流程定义、表单、流程实例和审批任务，例如我的申请、待办、已办。 |
| [`yudao-module-crm`](yudao-module-crm.md) | 客户关系管理（CRM） | 围绕客户、联系人、商机、合同和回款管理销售业务。 |
| [`yudao-module-erp`](yudao-module-erp.md) | 企业资源计划（ERP） | 围绕采购、销售、库存、产品和财务管理企业经营业务。 |
| [`yudao-module-fms`](yudao-module-fms.md) | 财务管理（FMS） | 面向凭证、账簿、财务报表和结账等财务业务。 |
| [`yudao-module-hrm`](yudao-module-hrm.md) | 人力资源管理（HRM） | 面向员工、招聘、考勤、薪资、社保和绩效等人事业务。 |
| [`yudao-module-im`](yudao-module-im.md) | 即时通讯（IM） | 面向单聊、群聊、消息收发、撤回及已读状态等即时聊天业务。 |
| [`yudao-module-iot`](yudao-module-iot.md) | 物联网（IoT） | 物联网聚合模块，组织设备管理业务、公共核心能力和设备接入网关。 |
| [`yudao-module-mall`](yudao-module-mall.md) | 电子商城 | 商城的聚合模块，将商品、营销、交易、统计等子模块组织在一起。 |
| [`yudao-module-member`](yudao-module-member.md) | 会员中心 | 管理面向业务用户的会员信息与会员中心；与 system 中的后台管理员账号用途不同。 |
| [`yudao-module-mes`](yudao-module-mes.md) | 制造执行（MES） | 面向生产现场，覆盖排班、设备、工具、生产、质量等制造业务。 |
| [`yudao-module-mp`](yudao-module-mp.md) | 微信公众号 | 管理公众号账号、菜单、粉丝、消息、自动回复、素材和模板通知。 |
| [`yudao-module-oa`](yudao-module-oa.md) | 办公自动化（OA） | 面向日程、云盘、请假报销、会议室、车辆、印章和公文等协同办公业务。 |
| [`yudao-module-pay`](yudao-module-pay.md) | 支付系统 | 为业务提供支付和退款能力，管理商户、支付应用及相关订单。 |
| [`yudao-module-pms`](yudao-module-pms.md) | 项目管理（PMS） | 面向项目、工作事项、迭代、看板、工时和知识库等项目协作业务。 |
| [`yudao-module-report`](yudao-module-report.md) | 数据报表 | 接入报表工具，用于报表设计、打印、预览、导出及数据可视化。 |
| [`yudao-module-wms`](yudao-module-wms.md) | 仓库管理（WMS） | 面向仓储作业，管理仓库、物料、库存及入库、出库、移库、盘库。 |

## 框架组件：提供公共技术能力

这些组件主要被后端代码引用，通常不对应一个独立业务页面。`biz` 表示带有业务语义的公共组件，`starter` 表示用于接入并配置相应能力的 Spring Boot 组件。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-common`](yudao-framework/yudao-common.md) | 公共基础类型与工具 | 提供多个模块共用的基础对象、枚举和工具类。 |
| [`yudao-spring-boot-starter-biz-data-permission`](yudao-framework/yudao-spring-boot-starter-biz-data-permission.md) | 数据权限 | 封装数据访问范围控制，例如让不同用户只能查询其有权查看的数据。 |
| [`yudao-spring-boot-starter-biz-ip`](yudao-framework/yudao-spring-boot-starter-biz-ip.md) | IP 与行政区划 | 查询 IP 对应城市，以及行政区划编码对应的地区信息。 |
| [`yudao-spring-boot-starter-biz-tenant`](yudao-framework/yudao-spring-boot-starter-biz-tenant.md) | 多租户 | 封装多租户的底层支持，服务于不同租户的数据隔离。 |
| [`yudao-spring-boot-starter-excel`](yudao-framework/yudao-spring-boot-starter-excel.md) | Excel 导入导出 | 封装 Excel 处理能力，供业务模块导入、导出表格数据。 |
| [`yudao-spring-boot-starter-job`](yudao-framework/yudao-spring-boot-starter-job.md) | 定时与异步任务 | 封装 Quartz 定时调度与 Spring Async 异步任务；任务管理页面对应的后端业务位于 infra。 |
| [`yudao-spring-boot-starter-monitor`](yudao-framework/yudao-spring-boot-starter-monitor.md) | 监控与链路追踪 | 封装链路追踪、日志服务、指标收集等服务观测能力。 |
| [`yudao-spring-boot-starter-mq`](yudao-framework/yudao-spring-boot-starter-mq.md) | 消息队列 | 封装消息收发，支持 Redis、RocketMQ、RabbitMQ、Kafka 等消息队列。 |
| [`yudao-spring-boot-starter-mybatis`](yudao-framework/yudao-spring-boot-starter-mybatis.md) | 数据库访问 | 封装连接池、多数据源、事务和 MyBatis 扩展，支撑业务数据读写。 |
| [`yudao-spring-boot-starter-protection`](yudao-framework/yudao-spring-boot-starter-protection.md) | 服务保护 | 提供分布式锁、幂等、限流、熔断、API 签名等通用保护能力。 |
| [`yudao-spring-boot-starter-redis`](yudao-framework/yudao-spring-boot-starter-redis.md) | Redis 缓存 | 封装 Redis 的配置与访问扩展，供业务模块使用缓存等能力。 |
| [`yudao-spring-boot-starter-security`](yudao-framework/yudao-spring-boot-starter-security.md) | 认证与权限校验 | 处理用户身份认证、操作权限校验及操作日志等安全基础能力。 |
| [`yudao-spring-boot-starter-test`](yudao-framework/yudao-spring-boot-starter-test.md) | 测试支持 | 为单元测试和集成测试提供公共组件。 |
| [`yudao-spring-boot-starter-web`](yudao-framework/yudao-spring-boot-starter-web.md) | Web 接口基础能力 | 封装全局异常处理、API 日志、数据脱敏和错误码等 Web 通用能力。 |
| [`yudao-spring-boot-starter-websocket`](yudao-framework/yudao-spring-boot-starter-websocket.md) | WebSocket 实时通信 | 封装 WebSocket 长连接通信及多节点广播；IM 是其上层业务场景之一。 |

## 商城子模块：商品、营销、交易与统计

`yudao-module-mall` 是聚合入口，实际职责分到下面五个子模块。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-product`](yudao-module-mall/yudao-module-product.md) | 商品管理 | 管理品牌、商品分类、SPU（商品款式）和 SKU（具体规格商品）等商品资料。 |
| [`yudao-module-promotion`](yudao-module-mall/yudao-module-promotion.md) | 营销管理 | 管理营销活动、广告、优惠券和优惠码等促销业务。 |
| [`yudao-module-statistics`](yudao-module-mall/yudao-module-statistics.md) | 商城统计 | 汇总商品、会员和交易等商城经营数据。 |
| [`yudao-module-trade-api`](yudao-module-mall/yudao-module-trade-api.md) | 交易模块对外接口 | 向其他后端模块暴露交易能力的调用接口；具体交易业务由 trade 实现。 |
| [`yudao-module-trade`](yudao-module-mall/yudao-module-trade.md) | 交易管理 | 处理购物车、订单和退款等交易业务；支付渠道能力由 pay 模块提供。 |

## 物联网子模块：平台业务、公共核心与设备接入

`yudao-module-iot` 是聚合入口。gateway 负责与设备通信，biz 负责平台业务处理，两者复用 core 的公共能力。

| 模块（点击查看分析文档） | 中文名称 | 主要用途 |
| --- | --- | --- |
| [`yudao-module-iot-biz`](yudao-module-iot/yudao-module-iot-biz.md) | 物联网业务管理 | 处理产品与物模型、设备、OTA 升级、规则引擎、告警和数据统计等平台业务。 |
| [`yudao-module-iot-core`](yudao-module-iot/yudao-module-iot-core.md) | 物联网公共核心 | 提供 biz 与 gateway 共用的消息总线、设备查询 API 和数据解析脚本执行能力。 |
| [`yudao-module-iot-gateway`](yudao-module-iot/yudao-module-iot-gateway.md) | 设备接入网关 | 接收并解码设备上报消息，将平台消息编码后下发设备，适配 MQTT、HTTP、TCP 等接入协议。 |

## 容易混淆的模块

| 模块组合 | 职责区别 |
| --- | --- |
| system 与 member | system 管理使用后台的操作者及权限；member 面向业务用户的会员中心。 |
| system 与 security 组件 | system 提供用户、角色、菜单等管理业务；security 封装认证和权限校验的执行机制。 |
| infra 与 framework | infra 提供可管理的基础设施业务和研发工具；framework 提供业务代码复用的底层组件。 |
| infra 的任务管理与 job 组件 | infra 提供任务配置、管理及执行日志等业务；job 封装定时与异步任务的底层执行能力。 |
| trade 与 pay | trade 处理购物车、订单、退款等交易流程；pay 提供支付、退款等支付能力。 |
| ERP 与 WMS、MES、FMS | ERP 面向企业采购、销售、库存等整体经营业务；WMS 聚焦仓储作业，MES 聚焦生产现场，FMS 聚焦财务核算。业务范围可能重叠，不能据此认定它们互相依赖或已集成。 |
| AI 与 AI1 Spring AI | ai 侧重聊天、绘图、写作等大模型应用；ai1-springai 的声明定位侧重 Agent 编排以及 MCP、SKILL、知识库组合。 |
| WebSocket 组件与 IM | WebSocket 提供实时通信基础能力；IM 承载单聊、群聊、消息状态等聊天业务。 |

## 源码依据与文档约定

- 上游：[YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro)。
- 核对日期：2026-11-03；分支：`master`；提交：[`42528bff6de57761430db057e628f7c5c979d676`](https://github.com/YunaiV/ruoyi-vue-pro/commit/42528bff6de57761430db057e628f7c5c979d676)。
- 用途依据：[上游 README](https://github.com/YunaiV/ruoyi-vue-pro/blob/42528bff6de57761430db057e628f7c5c979d676/README.md) 与各模块 `pom.xml` 的 `description`；默认构建范围依据[根 pom.xml](https://github.com/YunaiV/ruoyi-vue-pro/blob/42528bff6de57761430db057e628f7c5c979d676/pom.xml)。
- 查找源码：表中模块名与上游目录名一致；子模块分别位于 `yudao-framework/`、`yudao-module-mall/`、`yudao-module-iot/` 下，各目录内的 `pom.xml` 可查看原始定位说明。
- 文档使用源码模块原名加 `.md`，子模块保留源码层级。本文仅补充用途介绍；实现原理、依赖关系、数据库设计及裁剪方案留待后续逐模块分析。
- 本导航针对上述后端快照。其他版本或分支需重新核对；`yudao-ui`、`sql`、`script` 等非后端 Maven 模块目录不在这 46 份文档清单内。
