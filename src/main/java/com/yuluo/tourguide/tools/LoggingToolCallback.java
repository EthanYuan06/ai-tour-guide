package com.yuluo.tourguide.tools;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * ToolCallback 观测装饰器：为所有工具补上框架日志缺失的耗时、conversationId 关联与生产可用的 info 级指标
 * 只观测不改写——返回值与异常语义完全透传，兜底文案由各工具自己负责
 */
@Slf4j
public class LoggingToolCallback implements ToolCallback {

    /**
     * 入参日志截断长度：Tavily / Firecrawl 入参可能很长，全量打印无观测价值
     */
    private static final int ARGS_LOG_LIMIT = 200;

    private final ToolCallback delegate;

    public LoggingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    /**
     * 必须委托：接口默认实现返回空元信息，会把被装饰工具的 returnDirect 等配置丢掉
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return doCall(toolInput, null);
    }

    /**
     * 必须覆写：框架带 ToolContext 走的是这个 default 重载，默认实现会降级调用 call(String) 并丢弃上下文
     */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return doCall(toolInput, toolContext);
    }

    private String doCall(String toolInput, ToolContext toolContext) {
        String name = getToolDefinition().name();
        Object conversationId = toolContext == null
                ? null : toolContext.getContext().get(ChatMemory.CONVERSATION_ID);
        long start = System.currentTimeMillis();
        try {
            String result = toolContext == null
                    ? delegate.call(toolInput)
                    : delegate.call(toolInput, toolContext);
            // info 级只留指标：返回正文可达数万字（Firecrawl 抓整页），写进生产日志不可接受，降到 debug
            log.info("[tool] name={} conversationId={} args={} cost={}ms resultChars={} blank={}",
                    name, conversationId, StrUtil.maxLength(toolInput, ARGS_LOG_LIMIT),
                    System.currentTimeMillis() - start,
                    result == null ? 0 : result.length(), StrUtil.isBlank(result));
            log.debug("[tool] name={} result={}", name, result);
            return result;
        } catch (Exception e) {
            // 原样抛出：装饰器不越层兜底，异常如何回给模型仍由 ToolExecutionExceptionProcessor 决定
            log.warn("[tool] name={} conversationId={} args={} cost={}ms 执行异常", name, conversationId,
                    StrUtil.maxLength(toolInput, ARGS_LOG_LIMIT), System.currentTimeMillis() - start, e);
            throw e;
        }
    }
}
