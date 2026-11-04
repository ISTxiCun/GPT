# V2 规划：缓存基础

**状态：核心 API 已实现。** 真实服务验收见下文；不承诺强一致。总约束见 [路线图](distributed-roadmap.md)，现有能力见 [V1](distributed.md)。

## 1. 范围与代码位置

已新增 `com.istxicun.backend.distributed.cache`，复用现有 Redis 连接及 `RedisKeyFactory`，优先提供显式 Java API。首版不做缓存注解、不引入本地二级缓存、不支持表达式驱动的任意方法缓存。

交付内容：类型明确的 get/load/evict、正值 TTL 随机化、空值缓存、按 Key 的热点加载保护、事务提交后失效的接入方式。业务模块决定缓存哪些数据、谁有权访问，以及允许多旧的数据。

## 2. 拟定接口与数据契约

| 拟定类型/方法 | 职责 |
|---|---|
| `CacheClient.get(key, type)` | 返回未命中、命中空值或命中数据三种状态 |
| `CacheClient.getOrLoad(key, type, policy, loader)` | 按读取策略回源，回源异常向调用方传播，不当作空值缓存 |
| `CacheClient.evict(key)` | 删除当前环境中指定 Key；不对业务开放任意通配符清库 |
| `CachePolicy` | 正值 TTL、空值 TTL、随机范围、加载等待和故障策略 |
| `CacheType<T>` 或等价类型令牌 | 保留泛型类型信息，避免列表反序列化为 Map |

写入包含格式版本、数据/空值标识及 JSON 负载的封装，使用显式目标类型反序列化；不开放任意类型自动实例化。空列表、0、false 与“不存在”严格区分。限制最大负载大小；格式损坏或未知版本按配置失效回源并记录指标，不能直接按错误类型返回业务。

Key 包含环境、缓存区域、结构版本和业务标识。权限相关结果必须包含必要的租户/用户范围，或在回源后再鉴权；哈希 Key 本身不提供授权。结构版本变化通过新 Key 逐步切换，旧值由 TTL 回收。

## 3. TTL 与热点加载流程

- 正值采用有限 TTL，加可配置范围的随机偏移；实际 TTL 必须始终大于零且有最大值。首版不开放永久缓存。
- 空值使用较短、独立且有上限的 TTL；缓存空值只能代表正常回源确认不存在，不能代表超时、权限失败或服务异常。
- 先实现 TTL 与空值功能，再增加按 Key 的加载保护，避免一次引入难以定位的组合行为。

热点加载的拟定流程：

1. 查询 Redis；命中数据或空值则返回。
2. 未命中后尝试获取该缓存 Key 的加载锁，复用 V1 看门狗锁，获取后再次读取缓存。
3. 仍未命中才调用 loader；正常结果写入缓存后返回。loader 抛错时不写空值。
4. 未获取加载锁的请求在有限等待内重查缓存；超过等待上限返回明确失败，或进入显式配置的降级策略，不无限轮询。
5. 始终释放锁；不跨异步线程持锁。启用此模式须同时开启 V1 锁功能，否则配置校验失败。

该机制用于减少常见条件下的重复回源，不承诺锁过期、进程暂停或故障时只回源一次。loader 必须是无副作用读取；写操作不能放进缓存 loader。

## 4. 更新、事务与一致性边界

默认推荐业务先更新数据库，成功提交后删除缓存。拟提供提交后执行失效动作的小型入口；无活动事务时立即执行，回滚时不执行。

此方案仍存在旧读回填、提交后进程崩溃、缓存删除失败等窗口，不能命名为“强一致缓存”。缓存失效失败不得把已经提交的数据库结果伪装成回滚。应留下可观测失败记录；有限重试仍失败时，由 TTL 控制陈旧上限。

需要可靠失效事件时，后续使用 V3 Outbox 将失效意图与业务写入置于同一事务。Outbox 只能增强事件可靠性，仍不能自动解决所有乱序回填，严格场景还需版本比较等业务方案。

首版不内置延迟双删、逻辑过期后台刷新、布隆过滤器、全局缓存扫描或自动跨模块联动失效。

## 5. 故障与配置计划

拟使用 `app.distributed.cache` 独立配置，默认关闭。TTL、空值 TTL、抖动范围、加载等待、最大负载和降级策略须校验边界，最终名称在编码前定稿。

Redis 读故障默认返回基础服务不可用；只有明确允许的只读场景可配置回源降级。回源降级失去跨实例加载保护，可能增加数据库压力，必须有并发预算与业务可接受性说明。

回源成功但缓存写入失败，拟返回已取得的数据并记录写缓存失败；若读取策略要求更严格，应另行明确。loader 自身异常、获取加载锁失败、编解码失败分别建模，不将它们全部吞掉。不得自动重试带业务副作用的动作。

## 6. 开发任务与验收

- [x] 定稿读取三态、泛型类型令牌、封装版本及迁移规则。
- [x] 实现独立开关、Key 隔离、负载大小与 TTL 参数校验。
- [x] 实现正值抖动、空值缓存、读取和删除。
- [x] 接入 V1 锁，实现二次检查、有界等待和异常释放。
- [x] 提供事务提交后失效入口，并写明失败窗口。
- [x] 添加真实 Redis/MySQL 测试与双实例缓存演示，更新使用文档。

验收至少覆盖：泛型集合往返、空列表与空值区分、TTL 边界和真实过期、空值命中减少回源、回源异常不污染缓存、两实例热点竞争、加载者崩溃/等待超时、事务提交/回滚、Redis 故障降级、旧格式处理、跨环境隔离。

缓存竞态测试要复现“提交后失效仍被旧读回填”的限制，不能只展示理想路径后声称数据强一致。演示放独立源码/构建配置，默认生产包不包含业务示例。

## 7. 已落地 API（cache-v2 / envelope-1）

启用 `app.distributed.cache.enabled=true`；热点保护额外启用
`app.distributed.lock.enabled=true`、`app.distributed.cache.protect-loads=true`。
所有参数见 `application.yml`。默认正值 TTL 300000ms、空值 10000ms、
随机偏移 ±30000ms、加载等待 1000ms、最大封装 1048576 字节。
正值含偏移必须在 1ms～1 天，空值 1ms～5 分钟，等待 0～30 秒。
默认不开启故障回源；启用须由业务确认数据库预算，单实例默认最多 8 个并发故障回源。

```java
var key = new CacheKey("products", "v1", tenantId + ":" + productId);
var type = CacheType.of(new TypeReference<List<ProductView>>() {});
List<ProductView> products = cache.getOrLoad(key, type, () -> repository.findViews(productId));
// 在事务服务中更新数据库后登记；回滚不删除，无事务立即删除。
cache.evictAfterCommit(key);
```

`get` 返回 `CacheResult.State.MISS/NULL/VALUE`，`getOrLoad` 返回加载结果（可为 null）。
可传入 `CachePolicy` 覆盖 TTL、等待、保护及故障回源；调用时校验保护所需锁。
开启保护时须在数据库事务外调用，同线程回源；失败获取锁在等待后再次读取并报
`CACHE_LOAD_TIMEOUT`，不无限轮询。锁服务故障不自动降级。

物理键为 `namespace:environment:dist:cache:<digest>`，digest 包含区域、结构版本和业务标识。
封装包含 `format=1`、目标类型规范名、`nil` 和 `payload`，绝不根据缓存内容实例化类型。
更换 DTO 结构须升级 CacheKey.version。`evict-invalid=true` 将未知格式、类型不符、超限或损坏项
视为失效并回源，以新封装覆盖；为避免误删并发写入，不主动删除旧项，未成功回填时由原 TTL 回收。
设为 false 则报 `CACHE_DECODE_FAILED`。

错误码还包括 `CACHE_READ_FAILED`、`CACHE_ENCODE_FAILED`、`CACHE_FALLBACK_BUSY`、
`CACHE_EVICT_FAILED`；基础分类为 UNAVAILABLE。loader 异常原样传播，锁错误保留 V1 错误码。
成功回源后 Redis 写入失败仍返回数据；编码失败单独报错。提交后删除失败记录日志和指标，
不会向事务调用方伪装数据库回滚；当前不自动重试，陈旧数据以 TTL 为界。
指标 `distributed.cache` 使用固定 `event` 标签，记录命中、空值、未命中、回源、降级及各阶段失败。

验证：`mvn test`；真实 Redis：`mvn -Predis-it -Dit.test=CacheRedisIT verify`。
该集成测试使用两个独立 Redisson 客户端，覆盖热点竞争、等待超时、空值过期和旧读回填窗口；
另通过独立 JVM 持锁后强制终止，验证看门狗锁过期后可重新加载。真实 MySQL 事务测试见 `CacheMysqlIT`。

MySQL 测试通过 `TEST_MYSQL_URL` 显式启用，同时设置 `TEST_MYSQL_USER`、`TEST_MYSQL_PASSWORD`；
Redis 使用现有 `TEST_REDIS_HOST/PORT/PASSWORD/DATABASE`。未配置 MySQL URL 时跳过该项。
双实例演示沿用 `compose.distributed-demo.yaml`，登录后携带同一 Bearer Token 分别请求
两实例 `/api/demo/cache`，`loadedBy` 应相同而 `instance` 不同；按用户隔离演示数据。
