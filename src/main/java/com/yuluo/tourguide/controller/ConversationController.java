package com.yuluo.tourguide.controller;

import com.yuluo.tourguide.common.BaseResponse;
import com.yuluo.tourguide.common.DeleteRequest;
import com.yuluo.tourguide.common.ResultUtils;
import com.yuluo.tourguide.model.dto.ConversationMessage;
import com.yuluo.tourguide.model.dto.ConversationSummary;
import com.yuluo.tourguide.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话记忆管理接口
 * 会话由首次对话隐式创建（无需独立创建接口），列表与历史均由记忆表聚合而来
 */
@Tag(name = "会话记忆管理")
@RestController
@RequestMapping("/conversation")
public class ConversationController {

    @Resource
    private ConversationService conversationService;

    /**
     * 查询会话列表，按最近活跃时间倒序，供前端刷新后恢复侧栏
     *
     * @return 会话摘要列表
     */
    @Operation(summary = "查询会话列表")
    @GetMapping("/list")
    public BaseResponse<List<ConversationSummary>> listConversations() {
        return ResultUtils.success(conversationService.listConversations());
    }

    /**
     * 查询某会话的历史消息，供前端点击会话时懒加载
     *
     * @param id 会话 id
     * @return 历史消息列表
     */
    @Operation(summary = "查询会话历史消息")
    @GetMapping("/{id}/messages")
    public BaseResponse<List<ConversationMessage>> listMessages(@PathVariable String id) {
        return ResultUtils.success(conversationService.listMessages(id));
    }

    /**
     * 删除会话及其全部消息，幂等
     *
     * @param deleteRequest 删除请求，id 为会话 id（String，规避 JS 精度丢失）
     * @return 是否删除成功
     */
    @Operation(summary = "删除会话")
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteConversation(@RequestBody DeleteRequest deleteRequest) {
        conversationService.deleteConversation(deleteRequest.getId());
        return ResultUtils.success(true);
    }
}