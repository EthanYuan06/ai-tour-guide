<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import SidebarNav from './components/SidebarNav.vue'
import SceneryBackground from './components/SceneryBackground.vue'
import ChatComposer from './components/ChatComposer.vue'
import MessageItem from './components/MessageItem.vue'
import ToastCard from './components/ToastCard.vue'
import { useChat } from './composables/useChat'

const { conversations, activeConversation, streaming, send, startNew, openConversation, dispose } =
  useChat()

const sidebarClosed = ref(window.innerWidth <= 860)
const welcomeText = ref('')
const chatText = ref('')

const toastVisible = ref(false)
let toastTimer: number | undefined
function onAttach() {
  toastVisible.value = true
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => (toastVisible.value = false), 2600)
}

const chatScroll = ref<HTMLElement | null>(null)
let stickBottom = true
function onChatScroll() {
  const el = chatScroll.value
  if (el) stickBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}
function scrollToBottom() {
  const el = chatScroll.value
  if (el) el.scrollTop = el.scrollHeight
}

// 流式增量或新消息到达时跟随滚动；用户主动上翻阅读时不打断
const lastSignal = computed(() => {
  const ms = activeConversation.value?.messages
  if (!ms?.length) return ''
  return `${ms.length}:${ms[ms.length - 1].content.length}`
})
watch(lastSignal, () => {
  nextTick(() => {
    if (stickBottom) scrollToBottom()
  })
})

function handleSend(text: string) {
  if (send(text)) {
    stickBottom = true
    nextTick(scrollToBottom)
  }
}
function handleOpen(key: number) {
  openConversation(key)
  stickBottom = true
  nextTick(scrollToBottom)
  if (window.innerWidth <= 860) sidebarClosed.value = true
}
function handleNewChat() {
  startNew()
  welcomeText.value = ''
}

const suggests = ['东京三日怎么玩？', '故宫半日速通路线', '上海夜景哪里拍最好看？']

onUnmounted(() => {
  window.clearTimeout(toastTimer)
  dispose()
})
</script>

<template>
  <!-- 全局 SVG symbol 库：图标 + 地标徽章 -->
  <svg width="0" height="0" style="position: absolute" aria-hidden="true">
    <defs>
      <g id="lm-pearl-shape">
        <rect x="48.5" y="2" width="3" height="14" rx="1.5" fill="#E14E86" />
        <circle cx="50" cy="22" r="6" fill="#F2679B" />
        <rect x="46" y="28" width="8" height="13" fill="#F9A8C6" />
        <circle cx="50" cy="51" r="11" fill="#F2679B" />
        <circle cx="46" cy="47" r="3" fill="#fff" opacity=".65" />
        <rect x="45.5" y="61" width="9" height="9" fill="#F9A8C6" />
        <circle cx="50" cy="79" r="14" fill="#F2679B" />
        <circle cx="45" cy="74" r="4" fill="#fff" opacity=".65" />
        <path d="M40 89 L32 98 M60 89 L68 98 M50 93 V98" stroke="#E14E86" stroke-width="3" stroke-linecap="round" fill="none" />
      </g>
      <g id="lm-tokyo-shape">
        <rect x="48.5" y="2" width="3" height="12" rx="1.5" fill="#E85D4A" />
        <path d="M50 12 C47 40 40 70 30 96 L70 96 C60 70 53 40 50 12 Z" fill="#F0705F" />
        <path d="M43 52 L57 64 M57 52 L43 64 M37 74 L63 92 M63 74 L37 92" stroke="#FFFDFC" stroke-width="2" opacity=".8" fill="none" />
        <rect x="38" y="44" width="24" height="8" rx="3" fill="#FFFDFC" />
        <rect x="33" y="66" width="34" height="7" rx="3" fill="#FFFDFC" />
      </g>
      <g id="lm-skytree-shape">
        <rect x="48.5" y="2" width="3" height="16" rx="1.5" fill="#4FA8C7" />
        <path d="M46 18 C45 45 40 72 34 96 L66 96 C60 72 55 45 54 18 Z" fill="#7CC7DE" />
        <path d="M44 72 L56 82 M56 72 L44 82" stroke="#FFFDFC" stroke-width="2" opacity=".75" fill="none" />
        <rect x="37" y="36" width="26" height="8" rx="4" fill="#4FA8C7" />
        <rect x="33" y="60" width="34" height="8" rx="4" fill="#4FA8C7" />
      </g>
      <g id="lm-palace-shape">
        <rect x="30" y="14" width="40" height="5" rx="2.5" fill="#E8A23C" />
        <path d="M30 19 Q50 14 70 19 L78 32 Q50 26 22 32 Z" fill="#FFC94D" />
        <rect x="30" y="32" width="40" height="11" fill="#E86A5E" />
        <path d="M24 41 Q50 36 76 41 L86 52 Q50 45 14 52 Z" fill="#FFC94D" />
        <rect x="24" y="52" width="52" height="22" fill="#E86A5E" />
        <rect x="30" y="54" width="4" height="18" fill="#C94F42" />
        <rect x="42" y="54" width="4" height="18" fill="#C94F42" />
        <rect x="54" y="54" width="4" height="18" fill="#C94F42" />
        <rect x="66" y="54" width="4" height="18" fill="#C94F42" />
        <rect x="46" y="58" width="8" height="16" fill="#8C3B33" />
        <rect x="18" y="74" width="64" height="6" fill="#F6E3D7" />
        <rect x="14" y="80" width="72" height="5" fill="#EAD5C8" />
      </g>
      <g id="lm-bigben-shape">
        <path d="M50 4 L62 30 L38 30 Z" fill="#C98A2E" />
        <rect x="36" y="30" width="28" height="62" fill="#E0A63C" />
        <circle cx="50" cy="46" r="10" fill="#FFFDFC" />
        <path d="M50 46 V39 M50 46 L55 49" stroke="#573B47" stroke-width="2" stroke-linecap="round" fill="none" />
        <rect x="42" y="62" width="16" height="4" rx="2" fill="#C98A2E" />
        <rect x="42" y="72" width="16" height="4" rx="2" fill="#C98A2E" />
        <rect x="42" y="82" width="16" height="4" rx="2" fill="#C98A2E" />
        <rect x="32" y="92" width="36" height="5" rx="2" fill="#C98A2E" />
      </g>
      <g id="icon-plane" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 13 L21 5 L13 21 L11 14 Z M11 14 L21 5" />
      </g>
      <g id="icon-compass" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="9" />
        <path d="M15.5 8.5 L13.5 13.5 L8.5 15.5 L10.5 10.5 Z" />
      </g>
      <g id="icon-globe" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="9" />
        <path d="M3 12 H21 M12 3 C8 7.5 8 16.5 12 21 C16 16.5 16 7.5 12 3" />
      </g>
      <g id="icon-clip" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M8 12 L14 6 Q17 3 19.5 5.5 Q22 8 19 11 L11 19 Q7.5 22.5 4.5 19.5 Q1.5 16.5 5 13 L12 6" />
      </g>
      <g id="icon-send" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 20 V5 M5.5 11.5 L12 5 L18.5 11.5" />
      </g>
    </defs>
  </svg>

  <div class="app">
    <SidebarNav
      :conversations="conversations"
      :active-key="activeConversation?.key ?? null"
      :closed="sidebarClosed"
      @toggle="sidebarClosed = !sidebarClosed"
      @new-chat="handleNewChat"
      @open="handleOpen"
    />

    <main class="main">
      <SceneryBackground />
      <button class="open-side" title="展开侧栏" @click="sidebarClosed = false">◧</button>

      <!-- 欢迎视图 -->
      <section v-if="!activeConversation" class="view welcome">
        <div class="welcome-inner">
          <h1 class="greeting">
            <svg class="sun" viewBox="0 0 40 40" aria-hidden="true">
              <circle cx="20" cy="20" r="9" fill="#FFCF5C" />
              <g stroke="#FFCF5C" stroke-width="3" stroke-linecap="round">
                <path d="M20 3 V8 M20 32 V37 M3 20 H8 M32 20 H37 M8 8 L11.5 11.5 M28.5 28.5 L32 32 M32 8 L28.5 11.5 M11.5 28.5 L8 32" />
              </g>
              <circle cx="17" cy="19" r="1.3" fill="#573B47" />
              <circle cx="23" cy="19" r="1.3" fill="#573B47" />
              <path d="M17 23 Q20 25.5 23 23" stroke="#573B47" stroke-width="1.6" fill="none" stroke-linecap="round" />
            </svg>我是小玲，你的旅行军师
          </h1>
          <ChatComposer
            v-model="welcomeText"
            placeholder="问问小玲，比如：帮我安排周六当天的出行方案"
            :streaming="streaming"
            @send="handleSend"
            @attach="onAttach"
          />
          <div class="suggests">
            <button v-for="q in suggests" :key="q" class="suggest" @click="handleSend(q)">
              <span class="q-star">✦</span>{{ q }}
            </button>
          </div>
        </div>
      </section>

      <!-- 对话视图 -->
      <section v-else class="view chat show">
        <div ref="chatScroll" class="chat-scroll" @scroll="onChatScroll">
          <div class="chat-column">
            <MessageItem
              v-for="(msg, i) in activeConversation.messages"
              :key="i"
              :message="msg"
              :streaming="streaming && i === activeConversation.messages.length - 1"
            />
          </div>
        </div>
        <div class="composer-wrap">
          <ChatComposer
            v-model="chatText"
            placeholder="继续追问，比如：第二天可以换成镰仓吗？"
            :streaming="streaming"
            @send="handleSend"
            @attach="onAttach"
          />
          <div class="chat-footnote">小玲的回答仅供参考，出行前请核实开放时间与票务信息</div>
        </div>
      </section>
    </main>

    <ToastCard :show="toastVisible" />
  </div>
</template>