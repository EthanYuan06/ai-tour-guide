package com.yuluo.tourguide.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话记忆表实体类
 * 表名必须小写：MyBatis-Flex 在 PostgreSQL 方言下会给标识符加双引号，
 * 带引号标识符大小写敏感，大写写法会报 relation "SPRING_AI_CHAT_MEMORY" does not exist
 */
@Data
@Table("spring_ai_chat_memory")
public class ChatMemoryEntity {

    @Column("conversation_id")
    private String conversationId;

    private String content;

    /**
     * 消息类型，取值 USER / ASSISTANT / SYSTEM / TOOL
     */
    private String type;

    /**
     * 消息时间，对应表列 TIMESTAMP；PG 下 MyBatis-Flex 按方言用双引号转义 "timestamp"，
     * 与 schema-postgresql.sql 的带引号列名一致（回 MySQL 时由反引号转义）
     */
    @Column("timestamp")
    private LocalDateTime timestamp;
}