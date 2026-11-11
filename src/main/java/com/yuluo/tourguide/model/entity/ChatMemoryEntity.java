package com.yuluo.tourguide.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话记忆表实体，映射 Spring AI 自动建表的 SPRING_AI_CHAT_MEMORY
 * 仅用于 MyBatis-Flex 的条件查询与删除；写入仍由官方 JdbcChatMemoryRepository 负责
 * 该表无单列主键，故不声明 @Id
 */
@Data
@Table("SPRING_AI_CHAT_MEMORY")
public class ChatMemoryEntity {

    /**
     * 会话 id
     */
    @Column("conversation_id")
    private String conversationId;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 消息类型，取值 USER / ASSISTANT / SYSTEM / TOOL
     */
    private String type;

    /**
     * 消息时间，对应表列 TIMESTAMP；timestamp 为 MySQL 保留字，由方言自动转义
     */
    @Column("timestamp")
    private LocalDateTime timestamp;
}