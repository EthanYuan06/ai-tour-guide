package com.yuluo.tourguide.config;

import com.yuluo.tourguide.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * AI 对话相关装配：系统提示词模板与 ChatClient
 * 对话记忆复用 Spring AI 自动装配的 ChatMemory（MessageWindowChatMemory，默认窗口 20）
 */
@Configuration
public class ChatAiConfig {

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
     * 智能导游对话客户端，挂载会话记忆 Advisor 与日志 Advisor
     *
     * @param chatMemory      Spring AI 自动装配的对话记忆（MessageWindowChatMemory，默认窗口 20）
     * @param myLoggerAdvisor 自定义日志 Advisor，仅观测不改写请求/响应
     */
    @Bean
    public ChatClient tourGuideChatClient(ChatClient.Builder chatClientBuilder,
                                          ChatMemory chatMemory,
                                          MyLoggerAdvisor myLoggerAdvisor) {
        return chatClientBuilder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        myLoggerAdvisor)
                .build();
    }
}
