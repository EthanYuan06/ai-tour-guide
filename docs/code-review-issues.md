# 代码审查待修复清单

> 活文档：每次代码审查后增量更新。仅保留当前待修复项；已修复项与「对当前代码没必要 / 负面优化」项即时清除。
> 分级：高（影响程序，优先修复）/ 中（记录，择机修复）/ 低（记录，可选）。

最后更新：2026-11-11（会话模块精简：MyBatis-Flex 接管查询/删除、删标题空白折叠与日期分组、DeleteRequest 改 String、修复 JsonConfig；清除已解决项 1 高 / 4 中 / 1 低）

## 高优先级

- [ ] `ConversationTokenAccumulator` 缺少 `@PreDestroy` 取消定时清理：Hutool `GlobalPruneTimer` 使用非守护线程（`ThreadUtil.newThread(r, name)` → `setDaemon(false)`），Spring 上下文关闭后周期任务不取消 → JVM 无法自然退出（SIGTERM 需等 grace period 被 SIGKILL）；且每次上下文启动都向共享单例注册新任务并强引用 `TimedCache`，测试 / devtools 场景下任务与 ClassLoader 持续泄漏。修复：`@PreDestroy` 中调用 `cache.cancelPruneSchedule()`（Hutool 5.8.48 已提供该 API）
- [ ] `useChat.removeConversation` — `index` 在 `await` 前捕获，删除请求飞行期间若发生 `send` → `unshift`，数组前移导致 `splice(index, 1)` 误删新会话；双击删除同理可重入 — await 完成后按 key 重新 `findIndex` 定位再删除，并用 `removingKeys: Set<string>` 防重入

## 中优先级

- [ ] `message` 无最大长度校验：历史整改计划中约定的入参长度上限配置项未落地
- [ ] `ConversationTokenAccumulator.accumulate` 返回缓存内活对象而非快照：`synchronized` 块外被 `MyLoggerAdvisor.after()` 读取，`long` 字段非 `volatile` 且无 happens-before 边，同会话并发轮次下日志可能读到混合 / 过期快照（存储计数本身正确，影响限于日志准确性）。修复：同步块内返回不可变副本，使「快照」契约成立
- [ ] `MyLoggerAdvisor.adviseStream` 的 `onErrorResume` 将异常统一包装为 `IllegalStateException`：`before()` 抛出的 `BusinessException(PARAMS_ERROR)` 在 `GlobalExceptionHandler` 落入 `RuntimeException` 分支返回 500「系统错误」而非 400 参数错误，与 `ErrorCode` 语义化规范冲突。注：该包装与 `BaseAdvisor` 默认实现逐字一致，非本次引入的回归，但本次已显式接管该代码路径。修复：移除 `onErrorResume`，对齐官方 `SimpleLoggerAdvisor` 写法
- [ ] 流式拦截契约不完整 — `busy` 类只挂在 `<nav class="history">`，`handleNewChat` 无 `streaming` 判断，流式中点「开启新对话」会切走视图而 SSE 仍在回写旧会话；与 `removeConversation` 的代码级拦截不同通道 — `handleNewChat` 补 `if (streaming.value) return`，或将阻断上移到整个侧栏并统一为单一通道
- [ ] `conversationId` 校验仅长度上限 — 未做 UUID 格式白名单，任意 ≤36 字符字符串仍可污染记忆表键空间（上轮待修复项只落了长度一半） — `validateConversationId` 增加格式校验，如 `^[0-9a-fA-F-]{32,36}$`

## 低优先级

- [ ] `GlobalExceptionHandler.handleSseError` 残留 `/chat/gen/code` 路径判断：属其他项目复制残留，当前无 SSE 接口
- [ ] `ChatRequest` 未使用 JSR-303 注解校验：依赖 app 层手动 `ThrowUtils` 校验，声明式校验更清晰
- [ ] 请求体解析异常返回码语义不准：`HttpMessageNotReadableException` 落入通用 `RuntimeException` 分支返回 500，语义上更应映射为 400 参数错误
- [ ] 会话累计 token 仍为单机内存态：进程重启即丢失、多实例不共享，现仅靠 TimedCache 按访问过期 2 小时缓解键空间增长（ChatMemory 已随 JDBC 改造持久化到 MySQL，此条仅指 token 累计器）
- [ ] `MyLoggerAdvisor.hasTokens` 仅判断 `totalTokens > 0`：若模型只回传 prompt / completion 而省略 total，聚合后 total 恒为 0，该轮真实 token 会被整轮丢弃并记为「未上报」（百炼当前会回传 `total_tokens`，属潜在缺口）。修复：三个字段任一 > 0 即视为有效
- [ ] `MyLoggerAdvisor.after` 用 `String.valueOf(conversationId)` 作累计器 key：null 会退化为字符串 `"null"`，把所有匿名请求静默合并进同一计数桶。当前被 `before()` 的非空校验挡住，属防御性缺口
- [ ] `adviseStream` 覆写保留了 `Mono.just` / `publishOn` / `onErrorResume` 等冗余编排：1.1.2 官方 `SimpleLoggerAdvisor` 仅需「记录请求 → `chain.nextStream` → `aggregateChatClientResponse`」三步，收敛后可同时解掉上面的异常包装问题
- [ ] `MyLoggerAdvisor` 在 `info` 级别打印 AI 回复全文：流式聚合修复后该链路首次真正落全文，配合 `max-tokens: 6000` 单条日志可达数千 token，存在日志膨胀与对话内容进日志的敏感面。修复：全文降为 `debug`，`info` 只留截断摘要 + token 统计
- [ ] 本轮新增/修改的多个文件缺 EOF 换行（Java DTO、Controller、Service、useChat.ts、api.ts 等） — 违反阿里手册格式约定，diff 噪声大 — 编辑器/提交前统一补尾行
- [ ] 会话 id 长度校验双源 — `GuideApp` 与 `ConversationService` 各写一遍同一 36 阈值 — 抽公共校验方法或 Controller/App 统一调 Service 的 public 校验
- [ ] `ConversationController.delete` 的 `data` 恒为 `true` — Service 返回 void，Controller 硬编码 `success(true)`，Javadoc「是否删除成功」语义空转 — Service 返回 boolean/受影响行数，Controller 透传
- [ ] `useChat.ts` 模块顶层 `void loadConversations()` — import 即触发 fetch，不利于单测与 HMR 重放 — 移到显式 `init()` 或首次 `useChat()` 惰性触发
- [ ] `'user' | 'assistant'` 字面量联合在 `api.ts` 与 `useChat.ts` 各自声明 — 类型重复 — 抽 `MessageRole` 由 `api.ts` 导出复用
- [ ] `api.ts` HTTP 错与业务码错都抛 `Error` — 调用方无法区分是否重试/提示 — 定义 `ApiError extends Error` 携带 `httpStatus/code`
- [ ] `ConversationMessage/ConversationSummary` 保留 `Serializable + serialVersionUID` — JSON HTTP 序列化不需要，模板残留 — 择机清理（与项目其他 DTO 口径保持一致）
- [ ] `createdAt` 仍为窗口内最早消息时间 — 官方 `JdbcChatMemoryRepository` 每轮重写 timestamp，`MIN(timestamp)` 并非真实创建时间；本期已去掉日期分组与 `lastActiveAt`，不影响 UI，登录模块若需精确创建时间再引入元数据 — 暂记，择机评估
