package com.yuluo.tourguide.rag;

import com.yuluo.tourguide.utils.DocumentEtlUtils;
import com.yuluo.tourguide.utils.RegexTextSplitter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线索引 ETL 手动触发入口：嵌入连通性 → 全量入库（幂等两遍）→ 固定三问冒烟召回
 * 触网且消耗 DashScope 配额，普通 mvn test 不执行；手动运行加 VM 参数 -DragEtl=true
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfSystemProperty(named = "ragEtl", matches = "true")
class RagEtlPipelineTest {

    private static final String DOCS_DIR = "docs/rag";

    private static final String SPLIT_REGEX = "(?=【)";

    private static final int MAX_CHUNK_LENGTH = 800;

    private static final int EMBEDDING_DIMENSIONS = 1024;

    private static final int EXPECTED_TOTAL_CHUNKS = 78;

    private static final int SMOKE_TOP_K = 5;

    private static final List<String> SMOKE_QUERIES = List.of(
            "去日本旅游消费如何",
            "交通费贵不贵",
            "有什么需要注意的");

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void step1_embeddingConnectivity() {
        float[] vector = embeddingModel.embed("日本旅游交通消费");
        assertNotNull(vector);
        assertEquals(EMBEDDING_DIMENSIONS, vector.length,
                "嵌入维度与 vector_store 表不一致，检查 spring.ai.dashscope.embedding.options.dimensions");
        log.info("[rag-etl] 嵌入连通性验证通过，model={} 维度={}",
                embeddingModel.getClass().getSimpleName(), vector.length);
    }

    @Test
    @Order(2)
    void step2_ingestAll() {
        File[] markdownFiles = new File(DOCS_DIR).listFiles((dir, name) -> name.endsWith(".md"));
        assertNotNull(markdownFiles, "语料目录不存在：" + DOCS_DIR);
        assertTrue(markdownFiles.length > 0, "语料目录为空：" + DOCS_DIR);
        Arrays.sort(markdownFiles);

        // 连跑两遍验证幂等：按 source 先删后建，总条数不变
        for (int round = 1; round <= 2; round++) {
            int total = 0;
            for (File file : markdownFiles) {
                total += DocumentEtlUtils.ingest(vectorStore, new FileSystemResource(file),
                        new RegexTextSplitter(SPLIT_REGEX, MAX_CHUNK_LENGTH));
            }
            assertEquals(EXPECTED_TOTAL_CHUNKS, total, "第 " + round + " 遍入库切片总数不符");
            Long dbCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Long.class);
            assertEquals(EXPECTED_TOTAL_CHUNKS, dbCount, "第 " + round + " 遍入库后表内条数不符（幂等失败）");
            log.info("[rag-etl] 第 {} 遍入库完成，表内共 {} 条", round, dbCount);
        }

        Integer maxContentLength = jdbcTemplate.queryForObject(
                "SELECT MAX(LENGTH(content)) FROM vector_store", Integer.class);
        assertNotNull(maxContentLength);
        assertTrue(maxContentLength <= MAX_CHUNK_LENGTH, "存在超过块长上限的切片：" + maxContentLength);
    }

    @Test
    @Order(3)
    void step3_smokeSearch() {
        for (String query : SMOKE_QUERIES) {
            List<Document> hits = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(SMOKE_TOP_K).build());
            assertNotNull(hits);
            log.info("[rag-etl] 冒烟问题「{}」召回 {} 条", query, hits.size());
            for (Document hit : hits) {
                String text = hit.getText();
                String summary = text.length() > 60 ? text.substring(0, 60) + "…" : text;
                log.info("[rag-etl]   score={} source={} category={} | {}",
                        hit.getScore(),
                        hit.getMetadata().get(DocumentEtlUtils.METADATA_SOURCE),
                        hit.getMetadata().get(DocumentEtlUtils.METADATA_CATEGORY),
                        summary.replace('\n', ' '));
            }
        }
    }
}