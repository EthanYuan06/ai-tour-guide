/**
 * 后端接口统一封装
 * 不引入 axios：当前只有 GET/DELETE 两类调用，原生 fetch 足够
 */

/** 与后端 BaseResponse 对齐 */
export interface BaseResponse<T> {
  code: number
  data: T
  message: string
}

/** 与后端 ConversationSummary 对齐；createdAt 为 epoch 毫秒，Long 字段经 JsonConfig 序列化为字符串 */
export interface ConversationSummaryDTO {
  conversationId: string
  title: string
  messageCount: string
  createdAt: string
}

/** 与后端 ConversationMessage 对齐，role 直接复用前端消息模型取值 */
export interface ConversationMessageDTO {
  role: 'user' | 'assistant'
  content: string
}

/**
 * 发起请求并解包 data；HTTP 非 2xx 或业务码非 0 均抛错，由调用方决定降级或提示
 */
export async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = (await res.json()) as BaseResponse<T>
  if (body.code !== 0) throw new Error(body.message || '请求失败')
  return body.data
}