package com.yuluo.tourguide.service;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.yuluo.tourguide.common.ConversationConstants;
import com.yuluo.tourguide.exception.ErrorCode;
import com.yuluo.tourguide.exception.ThrowUtils;
import com.yuluo.tourguide.mapper.ChatMemoryMapper;
import com.yuluo.tourguide.model.dto.ConversationMessage;
import com.yuluo.tourguide.model.dto.ConversationSummary;
import com.yuluo.tourguide.model.entity.ChatMemoryEntity;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mybatisflex.core.query.QueryMethods.column;
import static com.mybatisflex.core.query.QueryMethods.count;
import static com.mybatisflex.core.query.QueryMethods.min;
import static com.yuluo.tourguide.model.entity.table.ChatMemoryEntityTableDef.CHAT_MEMORY_ENTITY;

/**
 * 会话记忆服务
 * 查询与删除走 MyBatis-Flex；对话记忆写入仍由官方 JdbcChatMemoryRepository 负责
 */
@Slf4j
@Service
public class ConversationService {

    @Resource
    private ChatMemoryMapper chatMemoryMapper;

    /**
     * 查询会话列表，按创建时间倒序
     * 两步查询：先聚合出会话摘要（条数、创建时间），再批量取每会话首条用户消息作标题，避免相关子查询
     * 空表返回空集合而非 null；条数受 LIST_LIMIT 约束
     *
     * @return 会话摘要列表
     */
    public List<ConversationSummary> listConversations() {
        QueryWrapper summaryQuery = QueryWrapper.create()
                .select(CHAT_MEMORY_ENTITY.CONVERSATION_ID.as("conversationId"),
                        count(CHAT_MEMORY_ENTITY.CONVERSATION_ID).as("messageCount"),
                        // timestamp 列为 TIMESTAMP 类型，用 UNIX_TIMESTAMP 转为 epoch 秒再映射 Long
                        column("UNIX_TIMESTAMP(MIN(`timestamp`))").as("createdAt"))
                .from(CHAT_MEMORY_ENTITY)
                .groupBy(CHAT_MEMORY_ENTITY.CONVERSATION_ID)
                .orderBy(min(CHAT_MEMORY_ENTITY.TIMESTAMP).desc())
                .limit(ConversationConstants.LIST_LIMIT);
        List<ConversationSummary> summaries =
                chatMemoryMapper.selectListByQueryAs(summaryQuery, ConversationSummary.class);
        if (summaries.isEmpty()) {
            return summaries;
        }

        List<String> ids = summaries.stream().map(ConversationSummary::getConversationId).toList();
        Map<String, String> firstUserMessages = queryFirstUserMessages(ids);
        for (ConversationSummary summary : summaries) {
            summary.setTitle(buildTitle(firstUserMessages.get(summary.getConversationId())));
            // UNIX_TIMESTAMP 返回 epoch 秒，前端按毫秒解析，统一换算
            summary.setCreatedAt(summary.getCreatedAt() * 1000);
        }
        return summaries;
    }

    /**
     * 查询某会话的历史消息，按时间升序
     * 仅返回 USER/ASSISTANT 两类，避免未来引入工具调用后 SYSTEM/TOOL 消息泄漏到前端；
     * 受 MessageWindowChatMemory 窗口约束，返回的是当前存活消息而非全量历史
     *
     * @param conversationId 会话 id
     * @return 历史消息列表，会话不存在时为空集合
     */
    public List<ConversationMessage> listMessages(String conversationId) {
        validateConversationId(conversationId);
        QueryWrapper query = QueryWrapper.create()
                .select(CHAT_MEMORY_ENTITY.TYPE, CHAT_MEMORY_ENTITY.CONTENT)
                .from(CHAT_MEMORY_ENTITY)
                .where(CHAT_MEMORY_ENTITY.CONVERSATION_ID.eq(conversationId.trim())
                        .and(CHAT_MEMORY_ENTITY.TYPE.in("USER", "ASSISTANT")))
                .orderBy(CHAT_MEMORY_ENTITY.TIMESTAMP.asc());
        return chatMemoryMapper.selectListByQuery(query).stream()
                .map(entity -> new ConversationMessage(entity.getType().toLowerCase(), entity.getContent()))
                .toList();
    }

    /**
     * 删除会话及其全部消息，幂等：会话不存在也视为成功，便于前端重试
     *
     * @param conversationId 会话 id
     */
    public void deleteConversation(String conversationId) {
        validateConversationId(conversationId);
        QueryWrapper query = QueryWrapper.create()
                .where(CHAT_MEMORY_ENTITY.CONVERSATION_ID.eq(conversationId.trim()));
        chatMemoryMapper.deleteByQuery(query);
        log.info("[conversation] 删除会话 id={}", conversationId);
    }

    /**
     * 批量取各会话时间最早的一条用户消息正文，按 timestamp 升序后每会话保留首条
     */
    private Map<String, String> queryFirstUserMessages(List<String> ids) {
        QueryWrapper query = QueryWrapper.create()
                .select(CHAT_MEMORY_ENTITY.CONVERSATION_ID, CHAT_MEMORY_ENTITY.CONTENT)
                .from(CHAT_MEMORY_ENTITY)
                .where(CHAT_MEMORY_ENTITY.TYPE.eq("USER")
                        .and(CHAT_MEMORY_ENTITY.CONVERSATION_ID.in(ids)))
                .orderBy(CHAT_MEMORY_ENTITY.TIMESTAMP.asc());
        return chatMemoryMapper.selectListByQuery(query).stream()
                .collect(Collectors.toMap(
                        ChatMemoryEntity::getConversationId,
                        ChatMemoryEntity::getContent,
                        (first, duplicate) -> first));
    }

    /**
     * 会话 id 校验：非空白且长度不超过记忆表列上限，查询与删除共用
     */
    private void validateConversationId(String conversationId) {
        ThrowUtils.throwIf(StrUtil.isBlank(conversationId), ErrorCode.PARAMS_ERROR, "会话 id 不能为空");
        ThrowUtils.throwIf(conversationId.trim().length() > ConversationConstants.ID_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR, "会话 id 长度不能超过 " + ConversationConstants.ID_MAX_LENGTH);
    }

    /**
     * 标题截取：空白兜底为默认文案，其余直接截取前 N 字（空白折叠属后期优化，不在核心链路处理）
     */
    private static String buildTitle(String rawTitle) {
        if (StrUtil.isBlank(rawTitle)) {
            return ConversationConstants.DEFAULT_TITLE;
        }
        return rawTitle.substring(0, Math.min(rawTitle.length(), ConversationConstants.TITLE_MAX_LENGTH));
    }
}