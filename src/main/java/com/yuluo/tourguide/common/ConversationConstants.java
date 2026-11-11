package com.yuluo.tourguide.common;

/**
 * 会话记忆相关常量
 * 集中定义避免魔法值，并保证对话入口与查询入口的校验口径一致
 */
public final class ConversationConstants {

    /**
     * 会话 id 最大长度，对齐记忆表列 conversation_id VARCHAR(36)
     */
    public static final int ID_MAX_LENGTH = 36;

    /**
     * 会话标题截取长度，侧栏单行展示用
     */
    public static final int TITLE_MAX_LENGTH = 30;

    /**
     * 标题兜底文案：滑动窗口截断导致会话内已无 USER 消息时使用
     */
    public static final String DEFAULT_TITLE = "新会话";

    /**
     * 会话列表返回条数上限，防止会话无界增长拖慢查询
     */
    public static final int LIST_LIMIT = 200;

    private ConversationConstants() {
    }
}