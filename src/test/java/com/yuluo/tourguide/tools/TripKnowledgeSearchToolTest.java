package com.yuluo.tourguide.tools;

import com.yuluo.tourguide.constant.RagConstant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一手记录检索工具单测：只覆盖检索口径传参、切片拼接与两条降级分支，不触库不触网
 * 用 Mockito 而非手写桩——VectorStore 有 add / delete 重载等无关抽象方法，手写桩要实现全部
 * 模型「该不该调用本工具」属选择行为，绕过模型的单测覆盖不到，由端到端验证负责
 */
@ExtendWith(MockitoExtension.class)
class TripKnowledgeSearchToolTest {

    private static final String QUESTION = "2025 年 7 月日本行程的住宿一共花了多少钱";

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private TripKnowledgeSearchTool tool;

    @Test
    void should_pass_rag_search_params_and_join_hit_texts() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder().text("住宿：新宿两晚共 24000 円").score(0.8712d).build(),
                Document.builder().text("交通：JR pass 7 日券 50000 円").score(0.5234d).build()));

        String result = tool.searchTripRecords(QUESTION);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals(QUESTION, request.getQuery());
        assertEquals(RagConstant.TOP_K, request.getTopK());
        assertEquals(RagConstant.SIMILARITY_THRESHOLD, request.getSimilarityThreshold());

        assertEquals("住宿：新宿两晚共 24000 円\n\n交通：JR pass 7 日券 50000 円", result);
    }

    @Test
    void should_format_scores_to_two_decimals_and_dash_when_missing() {
        List<Document> documents = List.of(
                Document.builder().text("a").score(0.8712d).build(),
                Document.builder().text("b").score(0.5d).build(),
                Document.builder().text("c").build());

        String scores = tool.formatScores(documents);

        assertEquals("0.87,0.50,-", scores);
        assertTrue(scores.contains("0.87"), "分数未保留两位小数: " + scores);
        assertFalse(scores.contains("0.8712"), "分数未按两位小数截断: " + scores);
    }

    @Test
    void should_return_empty_hint_when_no_hit() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertEquals(RagConstant.EMPTY_RESULT_HINT, tool.searchTripRecords(QUESTION));
    }

    @Test
    void should_return_empty_hint_when_store_returns_null() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);

        assertEquals(RagConstant.EMPTY_RESULT_HINT, tool.searchTripRecords(QUESTION));
    }

    @Test
    void should_degrade_to_failure_hint_when_search_throws() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("pgvector 不可用"));

        String result = assertDoesNotThrow(() -> tool.searchTripRecords(QUESTION));

        assertEquals(RagConstant.FAILURE_HINT, result);
    }
}
