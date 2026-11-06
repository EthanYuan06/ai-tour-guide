package com.yuluo.tourguide.controller;

import com.yuluo.tourguide.app.GuideApp;
import com.yuluo.tourguide.common.BaseResponse;
import com.yuluo.tourguide.common.ResultUtils;
import com.yuluo.tourguide.model.dto.ChatRequest;
import com.yuluo.tourguide.model.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 导游对话接口
 */
@Tag(name = "AI 导游对话")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final GuideApp guideApp;

    /**
     * 向 AI 导游提问，同步返回完整回复
     *
     * @param chatRequest 对话请求
     * @return 会话 id 与回复内容
     */
    @Operation(summary = "向 AI 导游提问（同步返回）")
    @PostMapping
    public BaseResponse<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        return ResultUtils.success(guideApp.chat(chatRequest));
    }

    /**
     * 向 AI 导游提问，SSE 流式返回
     * 
     * @param message         用户提问内容，必填
     * @param conversationId  会话 id，可选；不传时服务端生成并经 conversation 事件回传
     * @return SSE 事件流
     */
    @Operation(summary = "向 AI 导游提问（流式返回）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId) {
        return guideApp.chatStream(message, conversationId);
    }
}
