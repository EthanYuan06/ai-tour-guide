package com.yuluo.tourguide.tools;

import cn.hutool.core.util.StrUtil;
import com.yuluo.tourguide.constant.ToolConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * ToolCallback 观测装饰器：为所有工具补上框架日志缺失的耗时、conversationId 关联与生产可用的 info 级指标
 * 观测 + 异常降级：工具异常在此收口为降级文案，是三个工具唯一的公共兜底点
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
            // 降级而非上抛：DefaultToolCallingManager 的异常表只 catch ToolExecutionException，
            // 三方工具在结果转换阶段抛的裸 IllegalStateException（Tavily 失败响应 results 为 null 触发 Jackson NPE）
            // 会绕过框架兜底、直接终结整条流式链路。
            // 堆栈必须留在 warn 里：降级会把真 bug 变静默，只有日志能区分「网络抖动」与「代码写错」
            log.warn("[tool] name={} conversationId={} args={} cost={}ms 执行异常，已降级为提示文案", name,
                    conversationId, StrUtil.maxLength(toolInput, ARGS_LOG_LIMIT),
                    System.currentTimeMillis() - start, e);
            return ToolConstant.FAILURE_HINT;
        }
    }
}
