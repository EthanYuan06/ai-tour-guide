# 代码审查待修复清单

> 活文档：每次代码审查后增量更新。仅保留当前待修复项；已修复项与「对当前代码没必要 / 负面优化」项即时清除。
> 分级：高（影响程序，优先修复）/ 中（记录，择机修复）/ 低（记录，可选）。

最后更新：2026-11-07（流式 token 观测修复 + 会话累计计数）

## 高优先级

本轮无遗留：AI 对话整改的高优问题（无日志观测、冗余手写 ChatMemory Bean、Service 结构与注入、重试未收敛）已全部修复，并通过 `mvn test` 与真实接口双轮验证。

## 中优先级

- [ ] `conversationId` 无长度 / 格式上限校验：客户端可传超长字符串，InMemory 记忆无淘汰机制，会话键空间无限增长，存在内存泄漏面
- [ ] `message` 无最大长度校验：历史整改计划中约定的入参长度上限配置项未落地

## 低优先级

- [ ] `GlobalExceptionHandler.handleSseError` 残留 `/chat/gen/code` 路径判断：属其他项目复制残留，当前无 SSE 接口
- [ ] `ChatRequest` 未使用 JSR-303 注解校验：依赖 app 层手动 `ThrowUtils` 校验，声明式校验更清晰
- [ ] 请求体解析异常返回码语义不准：`HttpMessageNotReadableException` 落入通用 `RuntimeException` 分支返回 500，语义上更应映射为 400 参数错误
- [ ] 会话累计 token 与 ChatMemory 同为单机内存态：进程重启即丢失、多实例不共享，现仅靠 TimedCache 按访问过期 2 小时缓解键空间增长，未解决多实例一致性
