package com.yuluo.tourguide.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆装配：滑动窗口策略 + PostgreSQL 持久化存储（官方文档的显式装配写法），重启后会话历史可恢复
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * 对话记忆：滑动窗口策略 + PostgreSQL 持久化存储
     *
     * @param chatMemoryRepository JDBC 记忆存储库
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }
}
