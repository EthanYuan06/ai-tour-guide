import { computed, ref } from 'vue'
import { request, type ConversationMessageDTO, type ConversationSummaryDTO } from '../lib/api'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  /** 流式连接中断标记 */
  failed?: boolean
}

export interface Conversation {
  /** 会话唯一标识：服务端会话用 conversationId，本地新建未落库时用 tmp- 前缀 */
  key: string
  title: string
  /** 服务端会话 id，conversation 事件回传前为 null */
  conversationId: string | null
  messages: ChatMessage[]
  createdAt: number
  /** 历史消息是否已加载，用于点击会话时懒加载 */
  loaded: boolean
}

/** 标题截取长度，与后端 ConversationConstants.TITLE_MAX_LENGTH 对齐 */
const TITLE_MAX_LENGTH = 30

// 会话列表由后端记忆表恢复，刷新后不再丢失；消息按需懒加载
const conversations = ref<Conversation[]>([])
const activeKey = ref<string | null>(null)
const streaming = ref(false)
let source: EventSource | null = null

/**
 * 启动时从后端恢复会话列表
 * 失败静默降级为纯本地新会话，后端未启动也不阻塞首屏
 */
async function loadConversations() {
  try {
    const list = await request<ConversationSummaryDTO[]>('/api/conversation/list')
    // 拉取期间若已产生本地会话则合并而非覆盖，避免丢掉正在进行的对话
    const localIds = new Set(conversations.value.map((c) => c.conversationId))
    const restored: Conversation[] = list
      .filter((s) => !localIds.has(s.conversationId))
      .map((s) => ({
        key: s.conversationId,
        title: s.title,
        conversationId: s.conversationId,
        messages: [],
        createdAt: Number(s.createdAt),
        loaded: false,
      }))
    conversations.value = [...conversations.value, ...restored]
  } catch (err) {
    console.warn('[chat] 会话列表加载失败，降级为本地会话', err)
  }
}

void loadConversations()

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

  /**
   * 打开会话，历史消息未加载时懒加载
   * 流式进行中禁止切换，避免 SSE 分片写入与当前视图错位
   */
  function openConversation(key: string) {
    if (streaming.value) return
    activeKey.value = key
    const conv = conversations.value.find((c) => c.key === key)
    // 无服务端 id 的本地临时会话不请求历史
    if (!conv || conv.loaded || !conv.conversationId) return
    void loadMessages(conv)
  }

  /** 加载失败不改变激活态，messages 留空可再次点击重试 */
  async function loadMessages(conv: Conversation) {
    try {
      const list = await request<ConversationMessageDTO[]>(
        `/api/conversation/${conv.conversationId}/messages`,
      )
      conv.messages = list.map((m) => ({ role: m.role, content: m.content }))
      conv.loaded = true
    } catch (err) {
      console.warn('[chat] 历史消息加载失败', err)
    }
  }

  /**
   * 删除会话及其全部消息，流式进行中禁止删除
   * 异常向上抛出，由调用方提示用户
   */
  async function removeConversation(key: string): Promise<boolean> {
    if (streaming.value) return false
    const index = conversations.value.findIndex((c) => c.key === key)
    if (index < 0) return false
    const conv = conversations.value[index]
    // 本地临时会话尚未落库，无需调用删除接口
    if (conv.conversationId) {
      await request<boolean>('/api/conversation/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: conv.conversationId }),
      })
    }
    conversations.value.splice(index, 1)
    if (activeKey.value === key) activeKey.value = null
    return true
  }

  /** 发送一条消息并开启 SSE 流；返回是否实际发出 */
  function send(text: string): boolean {
    const trimmed = text.trim()
    if (!trimmed || streaming.value) return false

    let conv = activeConversation.value
    if (!conv) {
      conv = {
        key: `tmp-${Date.now()}`,
        title: trimmed.slice(0, TITLE_MAX_LENGTH),
        conversationId: null,
        messages: [],
        createdAt: Date.now(),
        loaded: true,
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
      // 只回写 conversationId，不改 key：改 key 需联动 activeKey 并触发侧栏节点重建，无收益
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
    removeConversation,
    dispose,
  }
}