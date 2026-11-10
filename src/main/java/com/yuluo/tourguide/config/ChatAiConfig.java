package com.yuluo.tourguide.config;

import com.yuluo.tourguide.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * AI 对话相关装配：系统提示词模板、ChatMemory 与 ChatClient
 * 对话记忆基于 JDBC Repository 持久化到 MySQL，重启后会话历史可恢复
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
     * 对话记忆：滑动窗口策略 + MySQL 持久化存储（官方文档的显式装配写法）
     * Repository 由 JDBC starter 自动装配（方言自动探测为 MySQL，启动时自动建表）；
     * 本 Bean 存在时 Spring AI 自动装配的 InMemory 版 ChatMemory 因 @ConditionalOnMissingBean 退让
     *
     * @param chatMemoryRepository JDBC 记忆存储库，消息落入 SPRING_AI_CHAT_MEMORY 表
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * 智能导游对话客户端，挂载会话记忆 Advisor 与日志 Advisor
     *
     * @param chatMemory      基于 MySQL 持久化的对话记忆（MessageWindowChatMemory，窗口 20）
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
