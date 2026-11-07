package com.yuluo.tourguide.advisor;

import cn.hutool.core.util.StrUtil;
import com.yuluo.tourguide.exception.ErrorCode;
import com.yuluo.tourguide.exception.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 对话日志 Advisor：仅负责观测
 */
@Slf4j
@Component
public class MyLoggerAdvisor implements BaseAdvisor {

    /**
     * 输出 token 告警阈值
     */
    private static final int OUTPUT_TOKEN_WARN_THRESHOLD = 6000;

    @Resource
    private ConversationTokenAccumulator tokenAccumulator;

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
     * 响应后：记录 AI 回复、本轮与会话累计 token，输出 token 超阈值时告警，不改写响应
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

        // 未上报有效 usage（全 0）时不计入累计，避免脏数据污染会话计数
        if (!hasTokens(usage)) {
            log.info("[chat] conversationId={} AI 回复: {} | 本轮 tokens 未上报", conversationId, reply);
            return response;
        }

        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        Integer totalTokens = usage.getTotalTokens();
        ConversationTokenAccumulator.SessionTokenUsage accumulated = tokenAccumulator.accumulate(
                String.valueOf(conversationId), promptTokens, completionTokens, totalTokens);

        log.info("[chat] conversationId={} AI 回复: {} | 本轮 tokens={} (prompt={}, completion={})"
                        + " | 会话累计 tokens={} (prompt={}, completion={}, 轮次={})",
                conversationId, reply, totalTokens, promptTokens, completionTokens,
                accumulated.getTotalTokens(), accumulated.getPromptTokens(),
                accumulated.getCompletionTokens(), accumulated.getTurns());
        if (completionTokens != null && completionTokens >= OUTPUT_TOKEN_WARN_THRESHOLD) {
            log.warn("[chat] conversationId={} 输出 tokens={} 已达阈值 {}，回复可能被截断",
                    conversationId, completionTokens, OUTPUT_TOKEN_WARN_THRESHOLD);
        }
        return response;
    }

    /**
     * 流式链路：对完整聚合响应做观测
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return Mono.just(request)
                .publishOn(getScheduler())
                .map(req -> before(req, chain))
                .flatMapMany(chain::nextStream)
                .transform(flux -> new ChatClientMessageAggregator()
                        .aggregateChatClientResponse(flux, resp -> after(resp, chain)))
                // 保持与 BaseAdvisor 默认流式链路一致的错误语义，交由全局异常处理接住
                .onErrorResume(e -> Flux.error(new IllegalStateException("Stream processing failed", e)));
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
     * usage 是否携带有效 token：流式未开启 usage 上报时框架返回全 0 对象，视同未上报
     */
    private boolean hasTokens(Usage usage) {
        return usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0;
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
