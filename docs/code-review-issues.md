# 代码审查待修复清单

> 活文档：每次代码审查后增量更新。仅保留当前待修复项；已修复项与「对当前代码没必要 / 负面优化」项即时清除。
> 分级：高（影响程序，优先修复）/ 中（记录，择机修复）/ 低（记录，可选）。

最后更新：2026-11-07（commit `eb42db5` 审查：会话 token 累计器的生命周期、并发与异常语义）

## 高优先级

- [ ] `ConversationTokenAccumulator` 缺少 `@PreDestroy` 取消定时清理：Hutool `GlobalPruneTimer` 使用非守护线程（`ThreadUtil.newThread(r, name)` → `setDaemon(false)`），Spring 上下文关闭后周期任务不取消 → JVM 无法自然退出（SIGTERM 需等 grace period 被 SIGKILL）；且每次上下文启动都向共享单例注册新任务并强引用 `TimedCache`，测试 / devtools 场景下任务与 ClassLoader 持续泄漏。修复：`@PreDestroy` 中调用 `cache.cancelPruneSchedule()`（Hutool 5.8.48 已提供该 API）

## 中优先级

- [ ] `conversationId` 无长度 / 格式上限校验：客户端可传超长字符串，InMemory 记忆无淘汰机制，会话键空间无限增长，存在内存泄漏面
- [ ] `message` 无最大长度校验：历史整改计划中约定的入参长度上限配置项未落地
- [ ] `ConversationTokenAccumulator.accumulate` 返回缓存内活对象而非快照：`synchronized` 块外被 `MyLoggerAdvisor.after()` 读取，`long` 字段非 `volatile` 且无 happens-before 边，同会话并发轮次下日志可能读到混合 / 过期快照（存储计数本身正确，影响限于日志准确性）。修复：同步块内返回不可变副本，使「快照」契约成立
- [ ] `MyLoggerAdvisor.adviseStream` 的 `onErrorResume` 将异常统一包装为 `IllegalStateException`：`before()` 抛出的 `BusinessException(PARAMS_ERROR)` 在 `GlobalExceptionHandler` 落入 `RuntimeException` 分支返回 500「系统错误」而非 400 参数错误，与 `ErrorCode` 语义化规范冲突。注：该包装与 `BaseAdvisor` 默认实现逐字一致，非本次引入的回归，但本次已显式接管该代码路径。修复：移除 `onErrorResume`，对齐官方 `SimpleLoggerAdvisor` 写法

## 低优先级

- [ ] `GlobalExceptionHandler.handleSseError` 残留 `/chat/gen/code` 路径判断：属其他项目复制残留，当前无 SSE 接口
- [ ] `ChatRequest` 未使用 JSR-303 注解校验：依赖 app 层手动 `ThrowUtils` 校验，声明式校验更清晰
- [ ] 请求体解析异常返回码语义不准：`HttpMessageNotReadableException` 落入通用 `RuntimeException` 分支返回 500，语义上更应映射为 400 参数错误
- [ ] 会话累计 token 与 ChatMemory 同为单机内存态：进程重启即丢失、多实例不共享，现仅靠 TimedCache 按访问过期 2 小时缓解键空间增长，未解决多实例一致性
- [ ] `MyLoggerAdvisor.hasTokens` 仅判断 `totalTokens > 0`：若模型只回传 prompt / completion 而省略 total，聚合后 total 恒为 0，该轮真实 token 会被整轮丢弃并记为「未上报」（百炼当前会回传 `total_tokens`，属潜在缺口）。修复：三个字段任一 > 0 即视为有效
- [ ] `MyLoggerAdvisor.after` 用 `String.valueOf(conversationId)` 作累计器 key：null 会退化为字符串 `"null"`，把所有匿名请求静默合并进同一计数桶。当前被 `before()` 的非空校验挡住，属防御性缺口
- [ ] `adviseStream` 覆写保留了 `Mono.just` / `publishOn` / `onErrorResume` 等冗余编排：1.1.2 官方 `SimpleLoggerAdvisor` 仅需「记录请求 → `chain.nextStream` → `aggregateChatClientResponse`」三步，收敛后可同时解掉上面的异常包装问题
- [ ] `MyLoggerAdvisor` 在 `info` 级别打印 AI 回复全文：流式聚合修复后该链路首次真正落全文，配合 `max-tokens: 6000` 单条日志可达数千 token，存在日志膨胀与对话内容进日志的敏感面。修复：全文降为 `debug`，`info` 只留截断摘要 + token 统计
