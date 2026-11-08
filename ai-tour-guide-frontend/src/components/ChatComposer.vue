<script setup lang="ts">
import { nextTick, ref } from 'vue'

const model = defineModel<string>({ required: true })
const props = defineProps<{
  placeholder: string
  streaming: boolean
}>()
const emit = defineEmits<{
  send: [text: string]
  attach: []
}>()

// MVP：两个胶囊为纯 UI 开关，不影响请求参数
const planOn = ref(false)
const searchOn = ref(false)
const area = ref<HTMLTextAreaElement | null>(null)

function autosize() {
  const el = area.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

function submit() {
  const text = model.value.trim()
  if (!text || props.streaming) return
  emit('send', text)
  model.value = ''
  nextTick(autosize)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submit()
  }
}
</script>

<template>
  <div class="composer">
    <textarea
      ref="area"
      v-model="model"
      rows="2"
      :placeholder="placeholder"
      @input="autosize"
      @keydown="onKeydown"
    ></textarea>
    <div class="composer-bar">
      <button class="chip" :aria-pressed="planOn" @click="planOn = !planOn">
        <svg viewBox="0 0 24 24"><use href="#icon-compass" /></svg>行程规划
      </button>
      <button class="chip mint" :aria-pressed="searchOn" @click="searchOn = !searchOn">
        <svg viewBox="0 0 24 24"><use href="#icon-globe" /></svg>智能搜索
      </button>
      <span class="spacer"></span>
      <button class="attach" title="添加附件" @click="emit('attach')">
        <svg viewBox="0 0 24 24"><use href="#icon-clip" /></svg>
      </button>
      <button class="send" title="发送" :disabled="streaming || !model.trim()" @click="submit">
        <svg viewBox="0 0 24 24"><use href="#icon-send" /></svg>
      </button>
    </div>
  </div>
</template>