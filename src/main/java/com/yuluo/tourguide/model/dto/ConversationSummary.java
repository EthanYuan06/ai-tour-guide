package com.yuluo.tourguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话摘要，用于前端侧栏会话列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary implements Serializable {

    /**
     * 会话 id
     */
    private String conversationId;

    /**
     * 会话标题，取该会话时间最早的一条用户消息前 N 字
     */
    private String title;

    /**
     * 当前存活的消息条数
     */
    private Long messageCount;

    /**
     * 会话创建时间，epoch 毫秒，列表按其倒序
     */
    private Long createdAt;

    private static final long serialVersionUID = 1L;
}