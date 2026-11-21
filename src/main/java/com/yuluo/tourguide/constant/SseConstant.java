package com.yuluo.tourguide.constant;

import lombok.experimental.UtilityClass;

/**
 * SSE 事件名常量
 * 前端按事件名订阅，名字在后端两处（GuideApp 流内降级帧、GlobalExceptionHandler）与 useChat.ts 间必须同源，
 * 否则改名只改一半的表现是「后端发了帧、前端静默丢弃」
 */
@UtilityClass
public class SseConstant {

    /**
     * 会话 id 下发事件名：流式首个事件，前端据此回写 conversationId
     */
    public static final String CONVERSATION_EVENT = "conversation";

    /**
     * 正文分片事件名
     */
    public static final String MESSAGE_EVENT = "message";

    /**
     * 正常结束事件名，错误帧也必须跟一个，前端靠它收敛 EventSource 自动重连
     */
    public static final String DONE_EVENT = "done";

    /**
     * 业务错误事件名；刻意避开浏览器 EventSource 保留的 error 语义，防止与连接层错误混淆
     */
    public static final String BUSINESS_ERROR_EVENT = "business-error";
}
