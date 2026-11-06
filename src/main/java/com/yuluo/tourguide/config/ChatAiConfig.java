package com.yuluo.tourguide.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * AI 对话相关装配：对话记忆、系统提示词模板与 ChatClient
 */
@Configuration
public class ChatAiConfig {

    /**
     * 基于内存的多轮会话记忆，服务重启后清空
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

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
     * 智能导游对话客户端，挂载会话记忆 Advisor
     */
    @Bean
    public ChatClient tourGuideChatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        return chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
