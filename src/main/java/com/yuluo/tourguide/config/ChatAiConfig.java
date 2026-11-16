package com.yuluo.tourguide.config;

import com.yuluo.tourguide.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * AI 对话相关装配：系统提示词模板、ChatMemory 与 ChatClient
 * 对话记忆基于 JDBC Repository 持久化到 MySQL，重启后会话历史可恢复
 */
@Configuration
public class ChatAiConfig {

    private static final double RAG_SIMILARITY_THRESHOLD = 0.3d;

    private static final int RAG_TOP_K = 5;

    /**
     * 混合式 RAG 模板：召回命中时优先依据一手语料，召回为空时保持导游人设正常对话。
     */
    private static final String HYBRID_RAG_PROMPT = """
            你现在回答用户的最新问题。

            参考资料（来自导游本人日本游一手记录，可能为空）：
            {question_answer_context}

            使用规则：
            1. 参考资料非空时，优先依据资料回答，资料中的金额、线路等事实细节不得用通用知识替代或编造；
               资料未覆盖的部分可结合通用旅游知识自然补充。
            2. 参考资料为空时，按你的人物设定正常回答，不要提及参考资料或检索过程。

            用户问题：{query}
            """;

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
     * 对话记忆：滑动窗口策略 + MySQL 持久化存储（官方文档的显式装配写法）
     *
     * @param chatMemoryRepository JDBC 记忆存储库
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    /**
     * 在线 RAG 检索 Advisor
     *
     * @param vectorStore pgvector starter 自动装配的向量库
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                        .topK(RAG_TOP_K)
                        .build())
                .promptTemplate(PromptTemplate.builder().template(HYBRID_RAG_PROMPT).build())
                .build();
    }

    /**
     * 智能导游对话客户端，挂载会话记忆、日志与 RAG 检索 Advisor
     *
     * @param chatMemory            基于 MySQL 持久化的对话记忆
     * @param myLoggerAdvisor       自定义日志 Advisor，仅观测不改写请求/响应
     * @param questionAnswerAdvisor 在线 RAG Advisor，默认 order=0，在日志 Advisor 之前改写用户消息；
     *                             勿设为 Integer.MAX_VALUE，会与链尾模型 Advisor 同序而被跳过
     */
    @Bean
    public ChatClient tourGuideChatClient(ChatClient.Builder chatClientBuilder,
                                          ChatMemory chatMemory,
                                          MyLoggerAdvisor myLoggerAdvisor,
                                          QuestionAnswerAdvisor questionAnswerAdvisor) {
        return chatClientBuilder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        myLoggerAdvisor,
                        questionAnswerAdvisor)
                .build();
    }
}
