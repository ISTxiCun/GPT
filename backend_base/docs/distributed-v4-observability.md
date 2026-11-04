# V4 规划：运行与观测

**状态：基础能力已实现，完整故障矩阵待真实依赖验收。** 本阶段完善指标、追踪、探针、停机和故障验证；不直接引入服务注册中心、业务网关或微服务拆分。方案见 [路线图](distributed-roadmap.md)。

## 1. 当前基础与拟用技术

已有 Actuator 独立管理端口、健康检查、请求 ID、日志 MDC 和 Spring Boot 优雅停机配置。本阶段在这些基础上完善，不重新实现 Spring Boot 已提供的机制。

拟采用 Micrometer 指标、Micrometer Tracing 的 OpenTelemetry bridge、OTLP 导出。指标默认选择 Prometheus 格式作为可选适配；追踪经 OpenTelemetry Collector 转发到演示后端。实现前按 Spring Boot 3.5.9/JDK 17 核实兼容版本，优先使用 BOM 管理，不同时接入多套 SDK 或重复使用 Java Agent 插桩。

追踪、指标导出和演示组件独立开关。关闭时不创建 exporter 或额外后台发送线程；现有健康检查和请求 ID 继续可用。采集系统故障时不得让业务请求无限等待。

## 2. 指标设计

| 范围 | 拟采集内容 |
|---|---|
| HTTP | 请求量、错误率、延迟、活动请求 |
| V1 锁 | 获取成功/失败、等待和持有时长、释放失败 |
| V1 限流/防重 | 通过、拒绝、降级、后端故障 |
| V2 缓存 | 命中、空值命中、未命中、回源耗时、等待超时、写入/失效失败 |
| V3 消息 | 发布确认失败/耗时、消费耗时、重试、最终失败、积压与 Outbox 最老待发送年龄 |
| 运行资源 | JVM、线程、连接池、Redis/MQ 客户端相关指标 |

HTTP 标签使用路由模板，不能使用实际含 ID 的 URL。禁止将 userId、Token、原始 Redis Key、messageId、requestId、traceId 作为指标标签。缓存区域、消费组、限流规则名等也必须来自受控集合；实现前列出标签基数预算。

应用处理结果以应用指标为准；broker 队列深度、数据库存储等需要对应组件 exporter/监控接口，不能假装仅靠客户端埋点即可准确获得。

告警规则先提供建议模板，如错误率、延迟、持续积压和依赖故障；窗口与阈值由实际部署确定，不作为所有业务通用的 SLO。

## 3. 链路与日志关联

- 使用 W3C Trace Context，优先记录 HTTP → 模块调用 → Redis/MQ 发布/消费的必要链路。并非每个普通方法都创建 Span。
- `requestId` 保持现有用途，新增 traceId/spanId 日志字段，区分请求关联与分布式追踪。不要把已有 requestId 强行当成 traceId。
- 对线程池任务采用受控上下文传递并在结束后清理，验证线程复用不会串请求。不能声称任意新线程都会自动继承上下文。
- MQ headers 只传递必要的 trace 上下文；重投、延迟重试的 Span/Link 关系需定稿，避免把数小时重试拼成无限延长的请求 Span。
- 不默认采集完整请求体、消息体、Authorization、Cookie 或数据库敏感参数。baggage 采用白名单，外部传入追踪上下文也需合法性和大小校验。
- 默认采样率、批量队列、导出超时和丢弃策略均有界。Collector 不可用时记录有限的诊断信息，不无限缓存或反复阻塞业务线程。

## 4. 健康与就绪探针

保留管理端口与业务端口分离。默认回环监听保持不变；容器编排需要访问探针时，通过专门配置开放到内部网络，不能直接把管理端点暴露公网。

- **Liveness**：进程是否需要重启，不把外部 Redis/MySQL/MQ 暂时故障直接当作进程死亡，避免重启风暴。
- **Readiness**：此实例是否应接收业务流量。按启用功能和故障策略纳入必要依赖；未启用 MQ 时不因 MQ 不存在而拒绝流量。
- 基础数据库、共享认证 Redis 通常是当前应用的就绪依赖；限流显式允许 OPEN 不代表整个应用可以忽略认证 Redis 故障。
- 如果外部代理不能访问管理端口，编排/探针路径应显式设计；健康详情默认不对外展示。

开启健康分组与探针的具体路径、超时和返回行为在实现时写入配置表，并用真实依赖故障测试。健康 UP 只代表已定义的探针通过，不承诺所有业务都可正确完成。

## 5. 优雅停机与负载均衡

制定统一时间预算：负载均衡摘流/传播等待 → 停止领取新工作 → 等待在途处理 → 关闭资源和导出剩余观测数据 → 超时强制退出。

HTTP 停机继续使用 Spring Boot 能力，并配置明确的 shutdown phase timeout。消息消费者需停止拉取并等待在途事务/ACK；未确认消息允许 broker 重投。Outbox 投递器停止领取新任务，未完成任务由租约恢复。锁执行回调不强行在另一个线程释放，终止场景仍需靠租约/看门狗与业务约束处理。

Nginx 的开源轮询配置不等同于编排系统主动就绪探针。演示必须明确实际如何摘流；生产可由编排平台 readiness 管理服务端点。不能仅加一个健康接口就声称已经完成无损发布。

## 6. 演示与故障验证

拟提供独立观测 Compose：两个应用实例、Redis，以及按测试场景启用的 MySQL/RabbitMQ、Collector、Prometheus 和一个追踪/展示后端。只启用验证所需组件，给出资源需求；展示界面默认绑定本机，凭据不提交。

演示源码、仪表盘和配置放在脚手架自己的演示目录，默认生产包不带测试接口。脚本记录请求结果、预期故障行为和恢复结果，不能只证明“页面能打开”。

| 场景 | 必须验证 |
|---|---|
| 正常双实例请求 | 能关联日志与链路，指标按路由模板聚合 |
| Redis 故障 | V1 CLOSED/OPEN 行为正确，缓存按策略失败或降级，指标可见 |
| MQ 故障 | 发布结果未知/失败有区分，重试有界，恢复后继续处理 |
| Collector/指标采集端故障 | 业务不被无限阻塞，观测队列有界 |
| 进程终止 | 在预算内摘流和停机；消息未确认任务可恢复 |
| 上下文切换 | HTTP、任务、消费日志不串 trace/request，敏感字段不泄漏 |
| 指标压力 | 动态业务 ID 不造成标签数量持续增长 |

故障脚本只操作本地演示资源，不修改生产服务。每项演示写明哪些风险已覆盖、哪些仍需业务或部署平台处理。

## 7. 开发任务与验收

- [x] 列出现有 Actuator、日志、停机行为，定稿新增内容。
- [x] 定稿观测开关、采样、资源预算和标签清单。
- [x] 接入 Micrometer/OTel 与有限的 HTTP、Redis、MQ 埋点。
- [ ] 完善上下文传递、清理、脱敏及导出故障处理。
- [ ] 完善健康分组和 HTTP/消费者/投递器停机顺序。
- [x] 提供仪表盘、告警建议、故障脚本和使用文档。
- [ ] 验证默认关闭兼容性，以及独立演示中上述故障矩阵。

完成标准是能定位失败、判断就绪和观察恢复过程；不等于自动扩容、自动业务补偿或已经完成生产高可用架构。


## 8. 实现配置与边界

观测演示见 [使用说明](../demo/observability/README.md)。
现有 RequestIdFilter、Actuator 管理端口、Boot graceful shutdown 均继续复用。
依赖版本由 Boot 3.5.9 BOM 管理，编译目标 JDK 17。

| 配置 | 默认值 / 含义 |
|---|---|
| TRACING_ENABLED | false，启用 Micrometer OTel bridge |
| TRACING_EXPORT_ENABLED | false，独立启用 OTLP exporter |
| TRACING_SAMPLE_PROBABILITY | 0.1 |
| OTLP_TRACES_ENDPOINT | http://localhost:4318/v1/traces |
| PROMETHEUS_ENABLED | false；启用后还须将 prometheus 加入 MANAGEMENT_ENDPOINTS |
| MANAGEMENT_ENDPOINTS | health,info |
| MANAGEMENT_ADDRESS | 127.0.0.1 |
| /actuator/health/liveness | 仅 livenessState，外部依赖故障不触发重启 |
| /actuator/health/readiness、业务端口 /readyz | readinessState、数据库、认证 Redis；MQ 启用时额外包含 messaging |

依赖失败时 readiness 返回 503；详情不公开。Redis 超时 3s、连接池连接超时 5s，
Rabbit 建连超时 5s；探针需按依赖组合配置足够超时，UP 不承诺所有业务成功。
OTLP 连接 1s、发送 2s，批量队列 1024、每批 128、间隔 2s、导出预算 2s；
队列满时 SDK 丢弃，不能无限缓存。追踪开关与导出开关均须明确开启才能远程发送。

指标预算：V1 lock result 最多 5 值、rate.limit 最多 4 值、submission 最多 3 值；
缓存 event 来自代码常量，不标记 region；MQ result 来自枚举/代码常量，不标记 consumer。
锁等待/持有、缓存回源、MQ confirm/consume 为无业务标签 Timer。
HTTP uri 最多 100 值，超过预算拒绝新增对应 meter；采用框架路由模板。
不把任意规则名、Redis Key、用户、消息或追踪标识加入指标。
Outbox pending/oldest.age 在投递周期刷新，代表应用库待发送状态，不是 broker 队列深度；
数据库故障期间保留上次采样，需结合数据库健康判断。

新增的完整双实例观测验收、Collector 中断、指标压力及真实 MQ 追踪结果见
[验证记录](observability-validation.md)。长时重试 Link 与真实编排摘流仍不在本次保证范围。


本次实际验证（JDK 17）：`mvn verify`、`mvn -Predis-it verify`、
`mvn -Pmessaging-it verify`、`mvn -Pdistributed-demo -DskipTests package` 均通过。
Redis profile 中可选 CacheMysqlIT 未配置 TEST_MYSQL_URL，因此跳过；
消息 profile 使用真实 MySQL/RabbitMQ。生产 jar 已检查不包含演示类/配置。
实际启动单实例演示验证 Prometheus 和 readiness，停止 Redis 时 liveness=200、
readiness=503，重启后 readiness=200。Compose 合并配置及故障脚本语法已检查。
后续已运行完整观测 Compose 与断言故障脚本，详见验证记录；不将本地结果等同于生产全场景验收。
