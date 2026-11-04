# V3：可靠异步处理

**状态：已实现核心消息能力、JDBC 消费幂等及可选 Outbox；验收与限制见第 7 节。** 本文第 1～6 节保留设计依据；最终 API、配置和运行约束以第 7 节为准。采用 RabbitMQ 4.1 quorum queues 与 Spring AMQP 3.2.8（由 Spring Boot 3.5.9 BOM 管理），不封装其他 MQ。见 [路线图](distributed-roadmap.md)。

## 1. 范围与代码位置

拟新增 `distributed.messaging`，分为消息契约、RabbitMQ 适配、消费处理、幂等扩展和可选 Outbox。默认关闭；消费者和后台投递线程只能在对应功能明确启用时启动。

提供可靠发布/消费的机制、失败记录和可控重投，不提供订单补偿规则或通用分布式事务。语义按**至少一次投递、业务负责可重复执行**设计，不宣称 exactly-once。

## 2. 消息与接口计划

消息封装拟包含 `messageId`、`eventType`、`schemaVersion`、`occurredAt`、生产者标识、JSON payload 和可选追踪上下文。ID 可使用 UUID；同一次消息的发布重试保留 ID。

消息体不使用 Java 原生序列化。限制大小，按 eventType/schemaVersion 显式选择 DTO；未知版本进入受控失败处理，不任意加载 Java 类型。路由、交换机、队列和消费组均配置环境隔离。

| 拟定入口 | 语义 |
|---|---|
| `MessagePublisher.publish(envelope)` | 返回 broker 发布确认的结果，不表示消费业务已完成 |
| 消费处理器 | 成功返回后才允许 ACK，异常由统一失败策略处理 |
| 幂等扩展接口 | 由业务选择数据库幂等实现，不能默认为内存去重 |
| 可选 `OutboxWriter.append(envelope)` | 在已有本地数据库事务中记录待发送消息 |

Publisher confirm 的 ACK 只说明 broker 接受发布。还需处理 mandatory return，避免消息无队列可路由却被当作成功；确认超时属于结果未知，不应直接认定消息没有发送。

## 3. 发布、消费和重试

拟配置持久消息、持久队列、publisher confirms/returns 和手动消费确认。部署时再明确队列类型、副本和 broker 高可用策略，客户端配置本身不提供整个系统的高可用保证。

- 消费者先校验消息封装和版本，再进入业务处理。存在数据库事务时，必须提交后 ACK。
- 可重试错误进入有界退避流程；不可恢复的格式/版本错误直接进入失败处理。禁止无限 requeue 形成紧循环。
- 初版拟用固定延迟等级的重试队列及死信路由，避免把 RabbitMQ 延迟插件作为默认前提。TTL 队列只提供近似延迟，须说明队头阻塞和精度限制。
- 还须验证 TTL 到期后的 broker 死信转发语义：经典队列默认转发不等于可靠投递。需要可靠转发时，应选用并配置支持至少一次死信投递的队列策略，覆盖目标队列不可用的测试；不能只验证生产者 confirm 就忽略这段链路。
- 向重试/失败队列转发时须确认新消息成功发布，再确认原消息。任何确认间隙都可能产生重复，不能假定只投递一次。
- 重试次数、延迟、prefetch、消费者并发和消息大小均有上限。达到上限进入失败队列并产生告警/记录。
- 失败重投必须有明确授权、审计及限速入口；首版可用运维脚本，不默认暴露公网 HTTP 管理接口。

首版不承诺跨队列顺序或全局有序；重试和并发会改变顺序。业务若需要顺序，应明确聚合标识、分区/队列策略和版本检查。

## 4. 消费幂等的边界

V1 防重复提交是短窗口 Redis 占位，不能直接作为可靠消费幂等。

对于同一 MySQL 事务内的业务写入，拟提供参考方案：`consumerName + messageId` 唯一约束，业务变更和“已处理”记录在同一事务提交。事务失败则一起回滚；不能先独立提交“已处理”再执行业务。

并发重复插入由数据库唯一约束裁决。多个消费者处理同一事件时按消费方名称分别记录。幂等记录的保留期须覆盖重试、故障恢复和人工重放窗口；清理后老消息再次投递可能重新执行。

外部支付、邮件、HTTP 调用等副作用不在本地事务内，必须使用对方的幂等键、业务流水或状态机。公共扩展接口不能替业务承诺这些操作只发生一次。

## 5. 可选 Outbox 子阶段

直接在数据库提交后调用 MQ 发布存在崩溃窗口。需要“数据库变更与发送意图一起落库”时，启用独立 Outbox 功能；不把简单 afterCommit 回调称为可靠投递。

拟定流程：

1. 业务事务内通过 `OutboxWriter` 插入基础设施事件，和业务变更共同提交。调用无事务时默认拒绝，避免误认为具备原子性。
2. 多实例投递器用 MySQL 8 的行锁/`SKIP LOCKED` 等方式短事务领取一批任务，写入领取者、租约和下次尝试时间。
3. 提交领取事务后再访问 MQ，不在数据库行锁内等待网络确认。
4. 发布确认后更新为已发送；确认失败按预算重试。进程崩溃或租约到期后可重新领取。
5. 更新任务状态须校验领取令牌，防止旧持有者覆盖新领取结果；即使如此，超时确认窗口仍可能重复发布，消费幂等不可省略。

Outbox 和消费幂等基础表通过**显式启用的独立迁移位置**创建，不能把 SQL 放进默认迁移目录导致关闭功能也自动建表。表名、状态机、索引、归档、清理和容量告警在编码前定稿。迁移具有持久影响，关闭开关不自动删表；回滚说明必须覆盖已产生的待发送事件。

Outbox 可以在核心发布/消费能力稳定后作为 V3 的独立子交付验收，不与普通发布接口混淆。

## 6. 开发任务与验收

- [x] 确认 RabbitMQ/Spring AMQP 版本、队列策略及消息版本规则。
- [x] 定稿发布结果、消费确认时点、重试分类及错误码。
- [x] 实现开关、连接配置、拓扑校验、发布 confirms/returns。
- [x] 实现手动 ACK、有界重试、失败队列和受控重投。
- [x] 提供数据库消费幂等扩展与参考事务实现。
- [x] 可选实现 Outbox 领取、租约、投递、显式迁移和保留清理入口；离线归档由运维负责。
- [x] 提供独立 MQ 演示环境、真实 RabbitMQ/MySQL 测试并更新文档。

验收至少覆盖：正常发布消费、无路由、broker 断连/恢复、确认超时、业务提交后 ACK 前进程退出、重复并发投递、毒消息、最大重试、失败消息重放、消息版本不兼容、Outbox 提交/回滚、多实例领取、领取者崩溃与租约恢复。

验收既检查消息最终能继续处理，也检查没有因为提前 ACK、吞异常或提交顺序错误而丢失处理机会。关闭功能时不连接 MQ、不启动消费者/投递器，也不创建可选表。

## 7. V3 实现（messaging-v3 / envelope-1）

### 7.1 开关与部署

代码位于 `com.istxicun.backend.distributed.messaging`。排除 Boot 的 Rabbit 自动配置，
由 `MessagingConfiguration` 独立配置连接。默认 `enabled=false`，不会创建 MQ 客户端、
拓扑、消费者或 Outbox 线程。默认迁移位置只有 `db/migration`，不含任何消息表。

| 配置 `app.distributed.messaging` | 默认 / 边界 |
|---|---|
| enabled / consumers-enabled / outbox.enabled | 均为 false；Outbox 必须同时开启消息功能 |
| host / port / virtual-host | localhost / 5672 / `/`；支持 RABBITMQ_HOST/PORT/VHOST |
| username / password | 无默认凭据；启用时必须显式设置 RABBITMQ_USERNAME/PASSWORD |
| tls | false；开启后校验证书链及主机名；生产跨主机连接应开启 |
| confirm-timeout-millis | 5000；100～30000 |
| max-message-bytes | 1048576；1024～16777216，含完整 JSON 封装 |
| prefetch / concurrency | 16 / 1；1～1024 / 1～16，每个消费者分别生效 |
| retry-delays-millis | [1000,10000,60000]；严格递增，各 100～3600000，最多 8 级；空列表表示不重试 |
| outbox.poll-millis / batch-size | 1000 / 10；100～60000 / 1～100 |
| outbox.lease-millis / max-attempts | 60000 / 10；1000～600000 / 1～100 |

Outbox 租约必须大于 `batch-size * confirm-timeout-millis + 1000`。
边界在配置绑定及组件构造时验证，开启所需的表不存在则启动失败。
只有注册 `MessageHandler<T>` Bean 且 `consumers-enabled=true` 时创建消费线程。
关闭消费但开启消息时仍声明已注册处理器的持久拓扑，可用于生产者先发布、稍后启动消费。

物理名为 `<namespace>.<environment>.msg.events/work/retry/failed`，以及
`<prefix>consumer.<consumerName>[.retry.<level>|.failed]`，唯一身份来源仍为 `app.identity`。
消费者名称及事件类型须为 1～64 位小写字母、数字、点或连字符，并以字母开头。
同进程消费者名称不能重复；同一消费组实例使用相同处理器声明和配置。
修改已有队列参数会由 RabbitMQ 明确拒绝，不能静默复用不兼容拓扑；需要迁移消费组名称并排空旧队列。
部署必须限制 vhost/用户权限，隔离名称不能代替 RabbitMQ ACL。

所有队列为 durable quorum，消息为 persistent，overflow 为 reject-publish。
重试队列配置 `x-dead-letter-strategy=at-least-once`、TTL 和显式 DLX/routing key，
不依赖延迟插件。验收使用真实 RabbitMQ 4.1；生产需至少三节点及持久磁盘、备份、容量告警。
单节点演示只验证协议和状态机，不提供高可用。

TTL 为近似延迟，存在队头阻塞；实际耗时还包括业务、积压及 broker 死信重试周期。
RabbitMQ 默认死信确认周期较长（4.1 默认 180 秒），不能用业务 TTL 当作端到端 SLA。
演示/CI 设置 `-rabbit dead_letter_worker_publisher_confirm_timeout 1000` 以加快故障测试，
生产应根据部署预算评估，而非直接照搬。

### 7.2 发布和消费 API

```java
MessageEnvelope envelope = codec.create("inventory.changed", 1, new InventoryChanged(productId));
PublishResult result = publisher.publish(envelope); // 在本地 DB 事务外调用
```

封装保留 UUID、eventType、schemaVersion、UTC occurredAt、生产者 namespace/environment、
JsonNode payload 和最多 8 项追踪上下文。单项追踪值最多 512 字符。发布重试必须复用 envelope 和 ID。
使用 UTF-8 JSON，不使用 Java 序列化或缓存内容指定的任意 Java 类。
处理器通过 `TypeReference<T>` 明确 DTO 和泛型，未知事件版本直接失败隔离。
不兼容升级应使用新 eventType/consumerName 并安排切换和旧队列排空；不能让同消费组混跑互不识别的 schema。

`PublishResult.Status` 为 CONFIRMED、RETURNED、NACKED、UNKNOWN。
只有同时获得 confirm ACK 且没有 mandatory return 才是 CONFIRMED；它不代表业务已消费。
异常、断连及确认超时为 UNKNOWN，可能已经发送成功。同 ID 重试仍可能重复投递。
活动数据库事务内直接 publish 被拒绝，要求使用 Outbox，防止误以为直接发布和数据库写入原子。

注册 `MessageHandler<T>` Bean，实现 consumerName/eventType/schemaVersion/payloadType/handle。
处理器必须同步完成并提交数据库事务后再返回；适配器之后才 ACK。不得遗留活动事务，
不得把异步 Future 的创建当作业务完成。对于本地 MySQL 写入可使用：

```java
var idempotency = new JdbcConsumerIdempotency(dataSource, identity);
// handle(envelope, payload) 内，businessService 使用同一 DataSource：
idempotency.execute("inventory-projection", envelope, () -> businessService.apply(payload));
```

该实现使用 REQUIRES_NEW 事务和 `(scope,consumer_name,message_id)` 唯一约束，
业务回调须加入该事务，不能另外开启独立事务或异步执行。
提交业务变更和消费记录；并发重复由数据库裁决。失败则一同回滚。
`ConsumerIdempotency` 是扩展接口；非同一 DataSource、外部 HTTP、支付和邮件不在保证范围。
幂等记录必须至少保留完整重试、恢复和人工重放窗口。默认不自动删除记录。

普通处理器异常逐级转发重试；`NonRetryableMessageException`、格式/版本错误直接进入失败队列。
重试级数耗尽进入失败队列。每次转发均先确认新消息，之后才 ACK 原消息。
转发不确认时保留原消息并停止该消费者，记录告警和指标；运维检查依赖、修复后重启实例。
这一选择避免无限 requeue 紧循环，但需要对暂停消费者告警并及时恢复。
正常重试恢复无需重启；TCP 断连由 Spring AMQP 重连，结果未知的发布仍由调用者决定重试。
不能吞业务异常或把失败吞成 ACK。确认间隙、进程暂停及连接故障可能重复，所有业务必须允许重复。
队列禁用 broker 自动 delivery-limit 丢弃，由应用重试预算与故障处置控制；需监控积压和重复率。

失败队列保留原 JSON；超限原消息保留受限截断内容并标记 `x-app-truncated`，禁止直接重放截断记录。
失败原因使用固定代码 INVALID_MESSAGE/HANDLER_FAILED，日志不输出消息体。
指标 `distributed.messaging.publish` 的 result 为发布状态；
`distributed.messaging.consume` 的 result 为 ack/retry/failed/forward-unknown；不以消息 ID 为标签。

### 7.3 显式数据库迁移与 Outbox

只需要发布消费时不要求新增表。选择 JDBC 幂等或 Outbox 后，显式设置：

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/messaging
app:
  distributed:
    messaging:
      enabled: true
      outbox:
        enabled: true
```

独立迁移 `db/messaging/V300__messaging_infrastructure.sql` 创建
`infra_message_consumed`、`infra_message_outbox`；共享数据库上的发布须先评审该持久迁移。
关闭开关不会删表或丢弃待发送意图。已有 schema 版本高于 300 的项目应先安排迁移版本，
不要通过忽略 Flyway 校验绕过冲突。

`OutboxWriter.append(envelope)` 要求同一 DataSource 的活动事务，和业务更新一起提交；
无事务拒绝，回滚不留下发送意图。messageId 在 scope 内唯一，重复 append 会报告数据库约束冲突。
多实例通过 MySQL 8 `FOR UPDATE SKIP LOCKED` 领取 PENDING 或租约过期的 IN_FLIGHT 任务，
并发扫描时可能返回空批次，由后续轮询继续领取，不保证每次拿满或公平领取。
在短事务内写入随机领取令牌、租约及 attempts 后提交，随后才访问 MQ。
确认成功写 SENT；失败/结果未知回 PENDING，以 2 的尝试次数次方秒退避，最多一小时。
预算耗尽转 FAILED 并告警；崩溃领取也消耗尝试预算。
所有完成更新检查 scope/messageId/status/claim_token，旧持有者不能覆盖新领取者结果。
租约不是全局只执行一次的保证；MQ 已接收但 SENT 未更新仍会重复发布。

`OutboxDispatcher.purgeSentBefore(before, limit)` 是显式保留操作，只删至少一天前的 SENT，
每次 1～1000 条。未自动归档正文或清理消费幂等记录；需要审计归档时先导出再清理。
监控 PENDING/IN_FLIGHT 最老年龄、FAILED 数量、表大小和磁盘空间；
`distributed.messaging.outbox` 记录 sent/retry/exhausted/stale-claim。
运维重投 FAILED Outbox 时应停投递器、审核业务幂等与事件内容、记录审计，再按 scope/messageId
将状态改为 PENDING、attempts=0、claim_token/lease_until 清空、next_attempt=当前时间。
禁止批量无条件更新整个共享表。关闭/回滚部署前，先排空或暂停并保存未发送事件，
保留迁移表及数据，恢复后按相同身份继续领取，不能直接回滚 SQL 删表。

### 7.4 授权失败重放与演示

`FailedMessageReplayer` 每秒最多一条，要求授权回调和审计回调；
审核失败消息后向消费组 work exchange 发布，confirm 成功、审计写入后才 ACK 失败队列原消息。
授权/审计/确认失败时保留原消息；确认未知后重试可能重复。重放将业务重试次数重置为 0。
不默认暴露 HTTP 管理入口。隔离演示构建提供本地 CLI：

```bash
mvn -Pdistributed-demo -DskipTests package
# 使用具备受限 RabbitMQ ACL 的运维账户，在受控主机上执行：
export MQ_REPLAY_AUTHORIZED=yes
export MQ_REPLAY_AUDIT_FILE=/secure/audit/messaging-replay.log
# APP_NAMESPACE/APP_ENV、RABBITMQ_HOST/PORT/VHOST/USERNAME/PASSWORD 必须对应目标部署。
java -Dloader.main=com.istxicun.demo.MessagingReplayCommand \
  -cp target-demo/backend-base-demo.jar org.springframework.boot.loader.launch.PropertiesLauncher inventory-projection
```

授权来自 OS 运维访问权限、专用 broker 凭据及显式确认环境；环境开关不是用户认证系统。
审计目录须由运维预建并限制权限，文件持锁、追加并 force 写盘，禁止用普通业务实例运行此工具。
同一审计文件的进程串行执行且间隔一秒；跨主机重投限速由运维调度协调。

启动演示基础设施前准备 `.env`：RABBITMQ_USERNAME/PASSWORD、DB_PASSWORD、
MYSQL_ROOT_PASSWORD、REDIS_PASSWORD，然后：

```bash
docker compose -f compose.messaging-demo.yaml up -d
# 导出 .env 中的服务凭据，DB_USERNAME=backend，DB_URL 指向演示 MySQL。
# APP_NAMESPACE/APP_ENV 在两实例保持相同；SERVER_PORT/MANAGEMENT_PORT 各自不同。
java -Dloader.main=com.istxicun.demo.MessagingDemoApplication \
  -cp target-demo/backend-base-demo.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

每个实例启动时写入一条 Outbox notice，两个实例竞争投递与消费，日志显示处理 ID。
演示不提供业务 HTTP 接口，消息与幂等演示代码只在 `distributed-demo` profile 编译，默认生产包排除。
示例消息只演示本地消费记录及日志，外部日志本身不具备事务 exactly-once 保证。

### 7.5 验证与范围

```bash
mvn verify
mvn -Pmessaging-it verify
mvn -Pdistributed-demo -DskipTests package
```

真实服务测试使用 TEST_RABBIT_HOST/PORT/USER/PASSWORD 和 TEST_MYSQL_URL/USER/PASSWORD。
默认 RabbitMQ 为本地 messaging/messaging-test，MySQL 为本地 messaging_test、root/messaging-test，
这些仅是测试默认值，生产无默认凭据。测试数据库须专用；Flyway 在测试库创建独立历史表。
不要将该命令指向生产数据库。原 `redis-it` 排除 Messaging*IT，独立 `messaging-it` 只运行消息测试。

验收覆盖消息确认/退回/超时、真实网络断连恢复、重试耗尽、失败重放、未知版本、
死信目标暂时无路由、MySQL 并发重复及回滚、Outbox 提交/回滚、领取排他、租约恢复和令牌隔离。
独立 JVM 在业务提交后、ACK 前退出，再投递时验证数据库副作用只执行一次。
Outbox 领取者崩溃通过保留未完成领取并将数据库租约置为过期确定性复现，不宣称已覆盖所有 OS/网络故障。
没有跨队列排序、跨数据库原子性、整个 broker 集群灾难恢复或 exactly-once 保证。

本次验证结果：默认单元/架构回归通过，原 `redis-it` V1/V2 回归通过；
`MessagingContractTest` 5 项、4 个真实消息集成场景通过；独立演示构建、
真实依赖演示启动及 Outbox notice 消费通过，授权 CLI 空队列审计通过。
服务验收环境为 RabbitMQ 4.1 与 MySQL 8.0.44；CI 使用 MySQL 8.4，CI 运行结果以 GitHub Actions 为准。
TLS 使用 JVM 默认信任库并启用主机名校验；本地验收为明文回环连接，未覆盖 TLS 握手及多节点 broker 故障。
