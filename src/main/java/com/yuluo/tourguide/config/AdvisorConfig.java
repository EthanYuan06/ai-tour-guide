package com.yuluo.tourguide.config;

import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Advisor 装配：在线 RAG 检索等对话流程拦截器
 */
@Configuration
public class AdvisorConfig {

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
}
