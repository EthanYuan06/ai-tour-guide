import { computed, ref } from 'vue'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** 流式连接中断标记 */
  failed?: boolean
}

export interface Conversation {
  /** 前端本地序号，兼作侧栏标题编号 */
  key: number
  title: string
  /** 服务端会话 id，conversation 事件回传前为 null */
  conversationId: string | null
  messages: ChatMessage[]
  createdAt: number
}

// MVP 边界：会话仅存内存，刷新即清空（与后端内存会话一致）
const conversations = ref<Conversation[]>([])
const activeKey = ref<number | null>(null)
const streaming = ref(false)
let seq = 0
let source: EventSource | null = null

export function useChat() {
  const activeConversation = computed(
    () => conversations.value.find((c) => c.key === activeKey.value) ?? null,
  )

  function closeStream() {
    source?.close()
    source = null
    streaming.value = false
  }

  function startNew() {
    activeKey.value = null
  }

  function openConversation(key: number) {
    activeKey.value = key
  }

  /** 发送一条消息并开启 SSE 流；返回是否实际发出 */
  function send(text: string): boolean {
    const trimmed = text.trim()
    if (!trimmed || streaming.value) return false

    let conv = activeConversation.value
    if (!conv) {
      seq += 1
      conv = {
        key: seq,
        title: `对话历史 ${seq}`,
        conversationId: null,
        messages: [],
        createdAt: Date.now(),
      }
      conversations.value.unshift(conv)
      // 经 Proxy 数组重新取回响应式代理：直接改裸对象不会触发视图更新
      conv = conversations.value[0]
    }
    activeKey.value = conv.key

    conv.messages.push({ role: 'user', content: trimmed })
    conv.messages.push({ role: 'assistant', content: '' })
    // SSE 回调必须持有响应式代理引用，逐片追加 content 才能触发打字机式渲染
    const reply = conv.messages[conv.messages.length - 1]
    streaming.value = true

    const params = new URLSearchParams({ message: trimmed })
    if (conv.conversationId) params.set('conversationId', conv.conversationId)
    const es = new EventSource(`/api/chat/stream?${params.toString()}`)
    source = es
    let done = false

    es.addEventListener('conversation', (e) => {
      conv!.conversationId = (e as MessageEvent<string>).data
    })
    es.addEventListener('message', (e) => {
      // 后端以 JSON 字符串封装分片，保留前导空格与换行
      reply.content += JSON.parse((e as MessageEvent<string>).data) as string
    })
    es.addEventListener('done', () => {
      done = true
      closeStream()
    })
    es.onerror = () => {
      // EventSource 默认自动重连，必须主动 close 收敛为重试一次都不做
      if (!done) {
        reply.failed = true
        reply.content = reply.content
          ? `${reply.content}\n\n> 连接中断，以上内容可能不完整。`
          : '连接中断，请检查后端服务后重试。'
      }
      closeStream()
    }
    return true
  }

  function dispose() {
    closeStream()
  }

  return {
    conversations,
    activeConversation,
    streaming,
    send,
    startNew,
    openConversation,
    dispose,
  }
}