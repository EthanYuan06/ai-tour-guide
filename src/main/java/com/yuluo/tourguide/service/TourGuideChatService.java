package com.yuluo.tourguide.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.yuluo.tourguide.exception.ErrorCode;
import com.yuluo.tourguide.exception.ThrowUtils;
import com.yuluo.tourguide.model.dto.ChatRequest;
import com.yuluo.tourguide.model.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 智能导游对话服务
 */
@Service
public class TourGuideChatService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ChatClient chatClient;

    private final PromptTemplate systemPromptTemplate;

    public TourGuideChatService(ChatClient tourGuideChatClient, PromptTemplate tourGuideSystemPrompt) {
        this.chatClient = tourGuideChatClient;
        this.systemPromptTemplate = tourGuideSystemPrompt;
    }

    /**
     * 单轮对话，基于 conversationId 维护多轮上下文
     *
     * @param chatRequest 对话请求
     * @return 会话 id 与 AI 回复
     */
    public ChatResponse chat(ChatRequest chatRequest) {
        ThrowUtils.throwIf(chatRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        String message = chatRequest.getMessage();
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "问题不能为空");

        String conversationId = StrUtil.isBlank(chatRequest.getConversationId())
                ? IdUtil.simpleUUID()
                : chatRequest.getConversationId().trim();

        String systemPrompt = systemPromptTemplate.render(
                Map.of("currentDate", LocalDate.now().format(DATE_FORMATTER)));

        String reply = chatClient.prompt()
                .system(systemPrompt)
                .user(message.trim())
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        ThrowUtils.throwIf(StrUtil.isBlank(reply), ErrorCode.SYSTEM_ERROR, "AI 未返回内容");
        return new ChatResponse(conversationId, reply);
    }
}
