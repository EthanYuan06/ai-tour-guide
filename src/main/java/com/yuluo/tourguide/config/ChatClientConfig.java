package com.yuluo.tourguide.config;

import com.yuluo.tourguide.advisor.MyLoggerAdvisor;
import com.yuluo.tourguide.tools.LoggingToolCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

/**
 * ChatClient 装配：系统提示词模板与智能导游对话客户端
 * 也是工具观测装饰器的唯一包装点
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    /**
     * 启动期读取并校验提示词文件，渲染交给 StTemplateRenderer
     */
    @Bean
    public PromptTemplate tourGuideSystemPrompt() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/tour-guide-system.st"))
                .build();
    }

    /**
     * 智能导游对话客户端
     *
     * @param chatMemory      基于 PostgreSQL 持久化的对话记忆
     * @param myLoggerAdvisor 自定义日志 Advisor，仅观测不改写请求/响应
     * @param toolCallbacks   容器内全部工具（tools/config/DefaultToolsConfig 装配），在此统一套观测装饰器
     */
    @Bean
    public ChatClient tourGuideChatClient(ChatClient.Builder chatClientBuilder,
                                          ChatMemory chatMemory,
                                          MyLoggerAdvisor myLoggerAdvisor,
                                          List<ToolCallback> toolCallbacks) {
        // 注入点统一包装：新增 ToolCallback bean 自动获得观测，避免「忘记包装就静默没有日志」
        // 类型见证不可省：List 泛型不变，map 会推断成 List<LoggingToolCallback> 而无法赋给 List<ToolCallback>
        List<ToolCallback> observedTools = toolCallbacks.stream()
                .<ToolCallback>map(LoggingToolCallback::new)
                .toList();
        // 启动即核对：应恰好三个，多出说明 starter 自动装配了额外 ToolCallback bean，需改回显式注入
        log.info("[chat] 已挂载工具: {}", observedTools.stream()
                .map(tool -> tool.getToolDefinition().name())
                .toList());
        return chatClientBuilder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        myLoggerAdvisor)
                .defaultToolCallbacks(observedTools)
                .build();
    }
}
