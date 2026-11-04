# 观测演示

先按 distributed-demo 文档设置 REDIS_PASSWORD、DEMO_ACCESS_KEY 并构建：

```sh
mvn -Pdistributed-demo -DskipTests package
docker compose -p backend-base-demo -f compose.distributed-demo.yaml -f compose.observability-demo.yaml up -d
```

建议预留 4 CPU、4 GiB 内存。两个应用共享 Redis；此演示没有数据库和 MQ，
MQ/MySQL 故障需另用 `compose.messaging-demo.yaml` 和 `mvn -Pmessaging-it verify`。
生产默认包不包含演示控制器。不要将演示 Compose 用于生产。

Prometheus http://localhost:9090，Jaeger http://localhost:16686，管理端口 18081/28081。
可将 dashboard.json 导入自己的 Grafana，选择 Prometheus 数据源。
Prometheus 中按 uri/status 查看 HTTP、按 event 查看缓存、按 result 查看 V1/MQ。
这些标签不包含业务 ID。Broker 队列深度需 RabbitMQ exporter，不能从 ACK 数量推算。

```sh
./demo/observability/faults.sh collector
./demo/observability/faults.sh redis
./demo/observability/faults.sh terminate
```

脚本只操作固定演示项目，记录故障前、故障中、恢复后的状态与时延，自动重启资源。
结果在 target-demo/fault-results，需要人工检查：Collector 停止时业务请求仍应在原有
依赖超时内返回；Redis 停止时 liveness 为 200、readiness 为 503；重启后 readiness
恢复 200。终止 app-a 时连接失败正常，app-b 应保持就绪。
脚本探针记录不代表完整业务故障验收：还需用 demo 登录和缓存/锁/限流接口产生流量，
对照指标与日志检查 CLOSED/OPEN、缓存策略、动态 ID 基数、MQ 重投和上下文清理。

Nginx 使用被动轮询，不读取 readiness。停机前生产编排器应先摘除端点、等待传播，
再发送 SIGTERM。演示直接 stop 不保证请求零失败，不表示已经实现无损发布。
应用 HTTP 使用 Boot graceful shutdown；每个 phase 最多 35s，Outbox 最多等待 30s，
消费者等待 30s 后关闭通道，未确认消息由 broker 重投。总退出预算须覆盖多个 phase，
建议编排器在摘流传播时间之外预留至少 120s，业务回调仍需有界。

追踪只使用 W3C，拒绝 HTTP baggage/tracestate，非法 traceparent 丢弃。
不采集请求体/消息体/认证头。默认采样 10%，演示采样 100%。
MQ 重试使用独立的有限时长消费 Span，并继承发布因果上下文；原 HTTP Span 不会保持打开。
这保留重试因果树，不等于重试耗时是 HTTP 延迟；长时间重试的跨 trace Link 尚未实现。
托管线程池传递观测上下文及 MDC，并恢复工作线程原值；任意自建线程不在此保证内。

完整断言验收可运行 `python3 demo/observability/verify.py`。它会停止并恢复本地演示组件，
生成 `target-demo/fault-results/acceptance.json`；失败返回非零。新增的动态路径与线程池
验证接口只进入 `distributed-demo` 包。管理和业务直连测试端口均仅绑定本机，分别为
18081/28081 与 18080/28080。启动前确保非 root 容器能读公共配置/jar：

```sh
chmod 644 target-demo/backend-base-demo.jar demo/nginx.conf demo/observability/*.yaml
```

实际执行结果与覆盖边界见 [验证记录](../../docs/observability-validation.md)。
