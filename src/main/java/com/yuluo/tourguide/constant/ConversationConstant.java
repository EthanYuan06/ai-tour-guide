package com.yuluo.tourguide.constant;

import lombok.experimental.UtilityClass;

/**
 * 会话记忆相关常量
 * 集中定义避免魔法值，并保证对话入口与查询入口的校验口径一致
 */
@UtilityClass
public class ConversationConstant {

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

    /**
     * 记忆表 timestamp 列写入的是无时区的本地墙上时间（JVM 与数据库均为东八区），
     * PostgreSQL 的 EXTRACT(EPOCH FROM timestamp) 会把该值按 UTC 解析、导致 epoch 多算 8 小时，
     * 故取 epoch 前需用此 zone 通过 AT TIME ZONE 显式声明时区，保持与前端毫秒契约一致
     */
    public static final String TIMESTAMP_ZONE = "Asia/Shanghai";
}
