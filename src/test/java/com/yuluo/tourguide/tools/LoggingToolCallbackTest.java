package com.yuluo.tourguide.tools;

import com.yuluo.tourguide.constant.ToolConstant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具观测装饰器单测：覆盖异常降级、ToolContext 透传与元信息委托，不触网不触模型
 * 异常降级是本次 SSE 断流事故的收口点：框架只 catch ToolExecutionException，裸 IllegalStateException 必须在此转成文案
 */
@ExtendWith(MockitoExtension.class)
class LoggingToolCallbackTest {

    @Mock
    private ToolCallback delegate;

    @Test
    void should_return_failure_hint_instead_of_rethrowing() {
        // 复刻事故现场：FunctionToolCallback 在结果转换阶段抛的 Jackson 序列化失败异常
        when(delegate.call(anyString()))
                .thenThrow(new IllegalStateException("Conversion from Object to JSON failed"));
        when(delegate.getToolDefinition()).thenReturn(mock(ToolDefinition.class));

        String result = new LoggingToolCallback(delegate).call("{\"query\":\"晴空塔\"}");

        // 断言与实现同源：文案改一个字，测试跟着走，不在两处复制字符串
        assertEquals(ToolConstant.FAILURE_HINT, result);
    }

    /**
     * 回归点：接口 default 的 call(String, ToolContext) 默认实现会降级调用 call(String) 并丢弃上下文，
     * 装饰器漏覆写时 conversationId 会静默变成 null，日志与工具侧都无从发现
     */
    @Test
    void should_pass_tool_context_through_to_delegate() {
        ToolContext toolContext = new ToolContext(Map.of(ChatMemory.CONVERSATION_ID, "c-1"));
        when(delegate.getToolDefinition()).thenReturn(mock(ToolDefinition.class));

        new LoggingToolCallback(delegate).call("{}", toolContext);

        verify(delegate).call(eq("{}"), any(ToolContext.class));
    }

    /**
     * 元信息必须委托：默认实现返回空元信息，被装饰工具的 returnDirect 等配置会被吃掉
     */
    @Test
    void should_delegate_definition_and_metadata_to_wrapped_callback() {
        ToolDefinition definition = mock(ToolDefinition.class);
        ToolMetadata metadata = mock(ToolMetadata.class);
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(delegate.getToolMetadata()).thenReturn(metadata);

        LoggingToolCallback callback = new LoggingToolCallback(delegate);

        assertSame(definition, callback.getToolDefinition());
        assertSame(metadata, callback.getToolMetadata());
    }
}
