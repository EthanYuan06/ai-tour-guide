package com.yuluo.tourguide.app;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yuluo.tourguide.exception.ErrorCode;
import com.yuluo.tourguide.exception.ThrowUtils;
import com.yuluo.tourguide.model.dto.ChatRequest;
import com.yuluo.tourguide.model.dto.ChatResponse;
import com.yuluo.tourguide.model.dto.ConversationMessage;
import com.yuluo.tourguide.model.dto.ConversationSummary;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 智能导游对话应用层
 */
@Slf4j
@Service
public class GuideApp {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private ChatClient tourGuideChatClient;

    @Resource
    private PromptTemplate tourGuideSystemPrompt;

    /**
     * 记忆存储库（JdbcChatMemoryRepository），历史消息查询用
     */
    @Resource
    private ChatMemoryRepository chatMemoryRepository;

    /**
     * 会话列表聚合查询用：Repository 接口不暴露时间戳，直接查记忆表
     */
    @Resource
    private JdbcTemplate jdbcTemplate;

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

        String conversationId = resolveConversationId(chatRequest.getConversationId());

        String systemPrompt = tourGuideSystemPrompt.render(
                Map.of("currentDate", LocalDate.now().format(DATE_FORMATTER)));

        String reply = tourGuideChatClient.prompt()
                .system(systemPrompt)
                .user(message.trim())
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        ThrowUtils.throwIf(StrUtil.isBlank(reply), ErrorCode.SYSTEM_ERROR, "AI 未返回内容");
        return new ChatResponse(conversationId, reply);
    }

    /**
     * 流式对话：以 SSE 事件流返回，首个事件下发会话 id，正文分片逐段下发
     *
     * @param message         用户提问内容
     * @param conversationId  会话 id，为空时由服务端生成并通过 conversation 事件回传
     * @return conversation → message* → done 事件流
     */
    public Flux<ServerSentEvent<String>> chatStream(String message, String conversationId) {
        // 非空由 @RequestParam 必填语义保证，此处只校验空白
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "问题不能为空");
        String resolvedConversationId = resolveConversationId(conversationId);

        String systemPrompt = tourGuideSystemPrompt.render(
                Map.of("currentDate", LocalDate.now().format(DATE_FORMATTER)));

        Flux<ServerSentEvent<String>> contentFlux = tourGuideChatClient.prompt()
                .system(systemPrompt)
                .user(message.trim())
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .stream()
                .content()
                // 百炼兼容模式首包可能是空串，过滤后避免下发空 data 事件
                .filter(StrUtil::isNotEmpty)
                // JSON 字符串封装：SSE 规范会剥掉 data 值的一个前导空格，裸文本分片会丢空格破坏 markdown
                .map(chunk -> ServerSentEvent.<String>builder(JSONUtil.quote(chunk)).event("message").build());

        return Flux.just(
                        ServerSentEvent.<String>builder(resolvedConversationId).event("conversation").build())
                .concatWith(contentFlux)
                .concatWith(Flux.just(ServerSentEvent.<String>builder("[DONE]").event("done").build()));
    }

    /**
     * 会话 id 解析：空白则生成新 id，否则去空格返回，同步/流式入口共用同一规则
     */
    private String resolveConversationId(String conversationId) {
        if (StrUtil.isBlank(conversationId)) {
            String newId = IdUtil.simpleUUID();
            log.info("[chat] 生成新会话 id={}", newId);
            return newId;
        }
        return conversationId.trim();
    }
}
