package com.yuluo.tourguide.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话历史消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage implements Serializable {

    /**
     * 消息角色，取值 user / assistant，与前端消息模型一致
     */
    private String role;

    /**
     * 消息正文
     */
    private String content;

    private static final long serialVersionUID = 1L;
}