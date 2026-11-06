package com.yuluo.tourguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 对话响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse implements Serializable {

    /**
     * 会话 id，后续提问需原样带回
     */
    private String conversationId;

    /**
     * AI 回复内容
     */
    private String reply;

    private static final long serialVersionUID = 1L;
}
