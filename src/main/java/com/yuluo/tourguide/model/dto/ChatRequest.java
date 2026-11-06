package com.yuluo.tourguide.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 对话请求
 */
@Data
public class ChatRequest implements Serializable {

    /**
     * 用户提问内容
     */
    private String message;

    /**
     * 会话 id，为空时由服务端生成
     */
    private String conversationId;

    private static final long serialVersionUID = 1L;
}
