package com.yuluo.tourguide.advisor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会话 token 累计器单测：只验证累加口径与会话隔离，TTL 淘汰行为由 Hutool TimedCache 保证，不在此覆盖
 */
class ConversationTokenAccumulatorTest {

    @Test
    void should_accumulate_usage_across_turns() {
        ConversationTokenAccumulator accumulator = new ConversationTokenAccumulator();

        accumulator.accumulate("conv-1", 100, 20, 120);
        ConversationTokenAccumulator.SessionTokenUsage usage = accumulator.accumulate("conv-1", 150, 30, 180);

        assertEquals(250L, usage.getPromptTokens());
        assertEquals(50L, usage.getCompletionTokens());
        assertEquals(300L, usage.getTotalTokens());
        assertEquals(2L, usage.getTurns());
    }

    @Test
    void should_keep_conversations_isolated() {
        ConversationTokenAccumulator accumulator = new ConversationTokenAccumulator();

        accumulator.accumulate("conv-1", 100, 20, 120);
        ConversationTokenAccumulator.SessionTokenUsage other = accumulator.accumulate("conv-2", 10, 5, 15);

        assertEquals(15L, other.getTotalTokens());
        assertEquals(1L, other.getTurns());
    }

    @Test
    void should_treat_null_usage_fields_as_zero() {
        ConversationTokenAccumulator accumulator = new ConversationTokenAccumulator();

        ConversationTokenAccumulator.SessionTokenUsage usage = accumulator.accumulate("conv-3", null, null, null);

        assertEquals(0L, usage.getPromptTokens());
        assertEquals(0L, usage.getCompletionTokens());
        assertEquals(0L, usage.getTotalTokens());
        assertEquals(1L, usage.getTurns());
    }
}
