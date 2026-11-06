package com.yuluo.tourguide.advisor;

import cn.hutool.core.util.StrUtil;
import com.yuluo.tourguide.exception.ErrorCode;
import com.yuluo.tourguide.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 对话日志 Advisor：仅负责观测，不参与对话记忆读写（记忆由 MessageChatMemoryAdvisor 承担）。
 * before 记录用户消息，after 记录 AI 回复与总消耗 token，输出 token 超阈值时告警。
 */
@Slf4j
@Component
public class MyLoggerAdvisor implements BaseAdvisor {

    /**
     * 输出 token 告警阈值，与 application.yml 中 max-tokens 保持一致
     */
    private static final int OUTPUT_TOKEN_WARN_THRESHOLD = 6000;

    /**
     * 请求前：前置校验 conversationId 并记录用户消息，不改写请求
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Object conversationId = request.context().get(ChatMemory.CONVERSATION_ID);
        // 前置防御校验：Service 已保证生成会话 id，正常不会触发
        ThrowUtils.throwIf(conversationId == null || StrUtil.isBlank(conversationId.toString()),
                ErrorCode.PARAMS_ERROR, "会话 id 不能为空");

        log.info("[chat] conversationId={} 用户消息: {}", conversationId, extractLatestUserText(request));
        return request;
    }

    /**
     * 响应后：记录 AI 回复与总消耗 token，输出 token 超阈值时告警，不改写响应
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return response;
        }

        Object conversationId = response.context().get(ChatMemory.CONVERSATION_ID);
        String reply = extractReplyText(chatResponse);
        Usage usage = chatResponse.getMetadata() == null ? null : chatResponse.getMetadata().getUsage();

        if (usage == null) {
            log.info("[chat] conversationId={} AI 回复: {}", conversationId, reply);
            return response;
        }

        log.info("[chat] conversationId={} AI 回复: {} | 总消耗 tokens={}",
                conversationId, reply, usage.getTotalTokens());
        Integer completionTokens = usage.getCompletionTokens();
        if (completionTokens != null && completionTokens >= OUTPUT_TOKEN_WARN_THRESHOLD) {
            log.warn("[chat] conversationId={} 输出 tokens={} 已达阈值 {}，回复可能被截断",
                    conversationId, completionTokens, OUTPUT_TOKEN_WARN_THRESHOLD);
        }
        return response;
    }

    /**
     * MessageChatMemoryAdvisor 默认 order = Integer.MIN_VALUE + 1000（最外层），
     * 本 Advisor 取 0 处于内层：before 在记忆加载后记录用户消息，after 在记忆写入前记录 AI 回复
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 从请求 prompt 中取最新一条用户消息文本
     */
    private String extractLatestUserText(ChatClientRequest request) {
        if (request.prompt() == null) {
            return "";
        }
        List<Message> messages = request.prompt().getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message.getMessageType() == MessageType.USER) {
                return message.getText();
            }
        }
        return "";
    }

    /**
     * 从模型响应中取 AI 回复文本
     */
    private String extractReplyText(ChatResponse chatResponse) {
        Generation result = chatResponse.getResult();
        if (result == null || result.getOutput() == null) {
            return "";
        }
        return result.getOutput().getText();
    }
}
