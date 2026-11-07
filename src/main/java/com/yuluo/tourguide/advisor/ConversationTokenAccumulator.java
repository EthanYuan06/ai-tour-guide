package com.yuluo.tourguide.advisor;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 会话级 token 累计器
 */
@Component
public class ConversationTokenAccumulator {

    /**
     * 按访问过期：2 小时无访问即淘汰，避免会话键空间无限增长
     */
    private static final long EXPIRE_AFTER_ACCESS_MS = 2 * 60 * 60 * 1000L;

    /**
     * 定时清理间隔：10 分钟
     */
    private static final long PRUNE_INTERVAL_MS = 10 * 60 * 1000L;

    private final TimedCache<String, SessionTokenUsage> cache = CacheUtil.newTimedCache(EXPIRE_AFTER_ACCESS_MS);

    /**
     * 启动定时清理，过期会话的计数随缓存一并回收
     */
    @PostConstruct
    public void startPrune() {
        cache.schedulePrune(PRUNE_INTERVAL_MS);
    }

    /**
     * 累加一轮 usage 并返回该会话的累计快照
     *
     * @param conversationId   会话 id
     * @param promptTokens     本轮输入 token
     * @param completionTokens 本轮输出 token
     * @param totalTokens      本轮总 token
     * @return 会话累计快照，返回对象为缓存内可变实例，调用方只读不存
     */
    public synchronized SessionTokenUsage accumulate(String conversationId,
                                                     Integer promptTokens,
                                                     Integer completionTokens,
                                                     Integer totalTokens) {
        SessionTokenUsage usage = cache.get(conversationId);
        if (usage == null) {
            usage = new SessionTokenUsage();
            cache.put(conversationId, usage);
        }
        usage.add(promptTokens, completionTokens, totalTokens);
        return usage;
    }

    /**
     * 会话累计 token，字段级 long 直加，轮次每计入一轮 +1
     */
    @Getter
    public static class SessionTokenUsage {

        private long promptTokens;

        private long completionTokens;

        private long totalTokens;

        /**
         * 已计入累计的对话轮次
         */
        private long turns;

        private void add(Integer prompt, Integer completion, Integer total) {
            this.promptTokens += nullToZero(prompt);
            this.completionTokens += nullToZero(completion);
            this.totalTokens += nullToZero(total);
            this.turns++;
        }

        private static long nullToZero(Integer value) {
            return value == null ? 0L : value;
        }
    }
}
