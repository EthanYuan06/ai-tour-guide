<script setup lang="ts">
import type { Conversation } from '../composables/useChat'

const props = defineProps<{
  conversations: Conversation[]
  activeKey: string | null
  closed: boolean
  /** 流式回复进行中：此时切换与删除被 useChat 拦截，侧栏同步给出视觉反馈 */
  busy: boolean
}>()

const emit = defineEmits<{
  toggle: []
  'new-chat': []
  open: [key: string]
  remove: [key: string]
}>()
</script>

<template>
  <aside class="sidebar" :class="{ closed }">
    <div class="brand">
      <span class="brand-badge">
        <svg viewBox="0 0 24 24" style="color: #fff"><use href="#icon-plane" /></svg>
      </span>
      <span class="brand-name">AI <em>导游</em></span>
      <button class="side-toggle" title="收起侧栏" @click="emit('toggle')">◨</button>
    </div>
    <button class="new-chat" @click="emit('new-chat')"><span class="plus">✚</span> 开启新对话</button>
    <nav class="history" :class="{ busy }">
      <button
        v-for="conv in conversations"
        :key="conv.key"
        class="history-item"
        :class="{ active: conv.key === activeKey }"
        :title="conv.title"
        @click="emit('open', conv.key)"
      >
        <span class="dot">✦</span>
        <span class="history-title">{{ conv.title }}</span>
        <span
          class="del"
          title="删除该会话"
          role="button"
          aria-label="删除该会话"
          @click.stop="emit('remove', conv.key)"
          >×</span
        >
      </button>
    </nav>
    <div class="user-card">
      <span class="avatar">Y</span>
      <span class="user-name">Yuan</span>
    </div>
  </aside>
</template>