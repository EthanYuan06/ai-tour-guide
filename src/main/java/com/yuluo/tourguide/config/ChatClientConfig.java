package com.yuluo.tourguide.config;

import com.yuluo.tourguide.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * ChatClient 装配：系统提示词模板与智能导游对话客户端
 */
@Configuration
public class ChatClientConfig {

    /**
     * 启动期读取并校验提示词文件，渲染交给 StTemplateRenderer
     */
    @Bean
    public PromptTemplate tourGuideSystemPrompt() {
        return PromptTemplate.builder()
                .resource(new ClassPathResource("prompts/tour-guide-system.st"))
                .build();
    }

    /**
     * 智能导游对话客户端
     *
     * @param chatMemory            基于 PostgreSQL 持久化的对话记忆
     * @param myLoggerAdvisor       自定义日志 Advisor，仅观测不改写请求/响应
     * @param questionAnswerAdvisor 在线 RAG Advisor，默认 order=0，在日志 Advisor 之前改写用户消息；
     *                             勿设为 Integer.MAX_VALUE，会与链尾模型 Advisor 同序而被跳过
     * @param tavilySearchToolCallback 联网搜索工具（tools/config/DefaultToolsConfig 包装）
     * @param fireCrawlToolCallback    网页抓取工具（tools/config/DefaultToolsConfig 包装）
     */
    @Bean
    public ChatClient tourGuideChatClient(ChatClient.Builder chatClientBuilder,
                                          ChatMemory chatMemory,
                                          MyLoggerAdvisor myLoggerAdvisor,
                                          QuestionAnswerAdvisor questionAnswerAdvisor,
                                          ToolCallback tavilySearchToolCallback,
                                          ToolCallback fireCrawlToolCallback) {
        return chatClientBuilder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        myLoggerAdvisor)
                // .advisors(questionAnswerAdvisor)
                .defaultToolCallbacks(tavilySearchToolCallback, fireCrawlToolCallback)
                .build();
    }
}
