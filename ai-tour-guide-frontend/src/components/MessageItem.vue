<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../lib/markdown'
import type { ChatMessage } from '../composables/useChat'

const props = defineProps<{
  message: ChatMessage
  /** 仅最后一条 assistant 消息在流式期间为 true */
  streaming: boolean
}>()

const html = computed(() => renderMarkdown(props.message.content))
const showTyping = computed(
  () => props.message.role === 'assistant' && props.streaming && !props.message.content,
)
</script>

<template>
  <div v-if="message.role === 'user'" class="msg-user">
    <div class="bubble">{{ message.content }}</div>
  </div>
  <div v-else class="msg-ai">
    <span class="ai-avatar">
      <svg viewBox="0 0 24 24" style="color: #fff"><use href="#icon-plane" /></svg>
    </span>
    <div class="bubble">
      <span v-if="showTyping" class="typing"><i></i><i></i><i></i></span>
      <!-- renderMarkdown 内部已做 DOMPurify 消毒 -->
      <div v-else class="md-body" v-html="html"></div>
    </div>
  </div>
</template>