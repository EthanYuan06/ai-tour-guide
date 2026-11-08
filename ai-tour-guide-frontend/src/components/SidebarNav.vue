<script setup lang="ts">
import { computed } from 'vue'
import type { Conversation } from '../composables/useChat'

const props = defineProps<{
  conversations: Conversation[]
  activeKey: number | null
  closed: boolean
}>()

const emit = defineEmits<{
  toggle: []
  'new-chat': []
  open: [key: number]
}>()

const groups = computed(() => {
  const out: { label: string; items: Conversation[] }[] = []
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  for (const conv of props.conversations) {
    const day = new Date(conv.createdAt)
    day.setHours(0, 0, 0, 0)
    const diff = Math.round((today.getTime() - day.getTime()) / 86400000)
    const label = diff <= 0 ? '今天' : diff === 1 ? '昨天' : diff <= 7 ? '7 天内' : '更早'
    const last = out[out.length - 1]
    if (last && last.label === label) last.items.push(conv)
    else out.push({ label, items: [conv] })
  }
  return out
})
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
    <nav class="history">
      <template v-for="group in groups" :key="group.label">
        <div class="history-group">{{ group.label }}</div>
        <button
          v-for="conv in group.items"
          :key="conv.key"
          class="history-item"
          :class="{ active: conv.key === activeKey }"
          :title="conv.title"
          @click="emit('open', conv.key)"
        >
          <span class="dot">✦</span>{{ conv.title }}
        </button>
      </template>
    </nav>
    <div class="user-card">
      <span class="avatar">Y</span>
      <span class="user-name">Yuan</span>
    </div>
  </aside>
</template>