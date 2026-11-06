package com.yuluo.tourguide.controller;

import com.yuluo.tourguide.common.BaseResponse;
import com.yuluo.tourguide.common.ResultUtils;
import com.yuluo.tourguide.model.dto.ChatRequest;
import com.yuluo.tourguide.model.dto.ChatResponse;
import com.yuluo.tourguide.service.TourGuideChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 导游对话接口
 */
@Tag(name = "AI 导游对话")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final TourGuideChatService tourGuideChatService;

    /**
     * 向 AI 导游提问，同步返回完整回复
     *
     * @param chatRequest 对话请求
     * @return 会话 id 与回复内容
     */
    @Operation(summary = "向 AI 导游提问（同步返回）")
    @PostMapping
    public BaseResponse<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        return ResultUtils.success(tourGuideChatService.chat(chatRequest));
    }
}
