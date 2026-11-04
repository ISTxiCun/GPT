# V4 观测验证记录

环境：JDK 17、Spring Boot 3.5.9；独立 Compose 使用两个应用实例、Redis 7.4、
Collector 0.123.0（核心发行版）、Prometheus 3.2.1、Jaeger 1.68.0、Nginx。
这些结果来自本地执行；PR 中的 CI 状态单独查看，不能据此推断生产 SLO。

## 可重复执行的验收

```sh
mvn verify
mvn -Predis-it verify
mvn -Pmessaging-it verify
mvn -Pdistributed-demo -DskipTests package
# 先设置自己的 REDIS_PASSWORD、DEMO_ACCESS_KEY；不提交凭据。
chmod 644 target-demo/backend-base-demo.jar demo/nginx.conf demo/observability/*.yaml
docker compose -p backend-base-demo -f compose.distributed-demo.yaml -f compose.observability-demo.yaml up -d
python3 demo/observability/verify.py
```

脚本操作固定的本地演示项目，停止并恢复 Collector、Prometheus、Redis、app-a。
每个阶段包含断言，失败返回非零；阶段结果记录在
`target-demo/fault-results/acceptance.json`，不记录 Token、密码或消息体。
GitHub Actions `observability-checks` 自动运行相同脚本并保存结果工件。
自建线程不属于 Boot 托管线程池的上下文传播保证。

最终本地 Maven 回归：68 个测试通过；另运行真实 RabbitMQ 的 `MessagingObservabilityIT`，
验证传播、延迟重试与在途停机通过。上一轮 Redis/MySQL/MQ 全量集成结果见 V4 主文档；
新增测试及完整 Compose 验收现已加入 CI，后续提交会重复执行。

## 本次实际结果

| 场景 | 执行与结果 |
|---|---|
| 双实例会话/缓存 | A 登录，A/B 使用同一 Token；两实例命中相同回源结果，通过 |
| HTTP → Redis → 任务上下文 | 20 个不同外部 W3C trace；HTTP 与任务 trace/span/request 一致，各请求 requestId 唯一，通过 |
| 追踪后端 | 从真实 Jaeger API 获取 HTTP/Redis Span，验证外部 traceId 与链路内容，通过 |
| 日志与敏感数据 | 检查双实例实际日志 requestId/traceId/spanId；日志、指标及导出 Span 无 Token/baggage 敏感标记，通过 |
| 动态 ID 压力 | 400 次动态路径请求，按 `/api/demo/observability/{id}` 聚合，指标 series 集合不增加，通过 |
| Collector + Prometheus 中断 | 同时停止两组件，16 并发执行 1600 次业务请求，全部成功；本次最大 0.147s、P95 0.082s，通过 |
| 采集恢复 | 重启组件后，新业务 trace 再次进入 Jaeger，通过 |
| Redis 故障恢复 | 两实例 liveness=200、readiness=503，业务认证请求失败；恢复后 readiness=200，通过 |
| 进程终止 | app-a 在 40s 预算内退出，app-b 保持就绪和业务成功；重启 A 后就绪恢复，通过 |
| 导出队列饱和 | 真实 OTel SDK + 阻塞 exporter，队列 32、批次 8；10000 个 Span 在 5s 内返回，接收最多 40 个，剩余有界丢弃，通过 |
| 开关与基数预算 | 追踪关闭/仅本地追踪/仅 Prometheus/双开关导出、100 路由上限的回归测试 |
| MQ 追踪 | 真实 RabbitMQ 验证 HTTP→发布→消费→200ms 延迟重试，两个无关请求不串 trace/requestId；Span 结束且不含消息体 |
| MQ 在途停机 | 消费回调阻塞时 stop 等待，回调结束后 ACK、队列无残留；超时退出仍由 broker 重投保证 |

压力时延只描述这次环境与负载，不作为性能承诺。观测故障期间允许丢失追踪数据，
不保证恢复后补齐已丢弃的 Span。HTTP 终止测试未证明负载均衡主动摘流或无损发布。
MQ 测试使用独立随机命名空间并清理队列；HTTP/MQ 消息体均不默认采集。

## 测试发现并处理的问题

1. Collector 重试 `max_elapsed_time=10s` 小于默认 `max_interval`，启动失败。
   显式设置初始间隔 1s、最大间隔 3s，真实启动和恢复验证通过。
2. 运行环境的代理变量使 Collector 经外部代理连接内部 Jaeger。
   为同一 Compose 网络内的 Jaeger 设置 NO_PROXY，保留其他代理配置。
3. Boot 的 exporter 专用开关可以覆盖全局 tracing=false，导致错误创建 exporter。
   使用受 tracing.enabled 控制的配置导入 Boot OTLP 自动配置，增加双开关回归。
4. Spring Boot 测试默认会禁用部分传播配置，造成 MQ 测试出现假断链。
   用 `@AutoConfigureObservability` 明确启用完整观测后，真实发布/消费/重试关系通过。
5. 严格 umask 导致非 root 容器不能读取公共配置/jar。
   文档及 CI 在启动前明确设置公共只读文件权限。

## 边界

未模拟数小时的延迟重试或真实编排平台摘流。当前 MQ 每次发布/消费均为有限时长
Span、保留因果父子关系；跨 trace Link 是后续可选扩展。Broker 深度需要组件 exporter，
不能由应用 ACK 计数推算。外部服务故障策略、业务补偿和生产资源预算仍需业务部署验收。
