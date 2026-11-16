package com.yuluo.tourguide.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 离线索引 ETL 通用工具：Markdown 读取 → 切分 → 元数据富化 → 先删后建写入向量库
 * 向量化由 VectorStore 注入的 EmbeddingModel 在 add 时完成，本类不直接调用嵌入模型
 */
@Slf4j
public final class DocumentEtlUtils {

    public static final String METADATA_SOURCE = "source";

    public static final String METADATA_CATEGORY = "category";

    private static final String ENTRY_HEADER_PREFIX = "【";

    private static final String ENTRY_HEADER_SUFFIX = "】";

    private static final String HEADER_FIELD_SEPARATOR = "｜";

    private DocumentEtlUtils() {
    }

    /**
     * Extract：读取 Markdown 为 Document，metadata 带 source=文件名
     */
    public static List<Document> readMarkdown(Resource resource) {
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata(METADATA_SOURCE, resource.getFilename())
                .build();
        return new MarkdownDocumentReader(resource, config).get();
    }

    /**
     * Transform：按切分器切片，基类自动把原文档 metadata 复制到每个切片
     */
    public static List<Document> split(List<Document> documents, TextSplitter splitter) {
        return splitter.apply(documents);
    }

    /**
     * Load：按 source 先删后建，重复执行不产生脏数据
     */
    public static void refresh(VectorStore vectorStore, String source, List<Document> chunks) {
        vectorStore.delete(new FilterExpressionBuilder().eq(METADATA_SOURCE, source).build());
        vectorStore.add(chunks);
    }

    /**
     * 单文件 ETL 编排：读取 → 切分 → 类别富化 → 先删后建入库
     *
     * @return 入库切片数
     */
    public static int ingest(VectorStore vectorStore, Resource resource, TextSplitter splitter) {
        List<Document> chunks = split(readMarkdown(resource), splitter);
        chunks.forEach(DocumentEtlUtils::enrichCategoryMetadata);
        String source = resource.getFilename();
        refresh(vectorStore, source, chunks);
        log.info("[rag-etl] source={} 入库切片 {} 条", source, chunks.size());
        return chunks.size();
    }

    /**
     * 切片以【类别｜…】元数据头开头时，提取类别词写入 metadata.category，供检索过滤表达式使用；
     * 会覆盖 MarkdownDocumentReader 自带的段落类型 category，无头切片保持原样
     */
    private static void enrichCategoryMetadata(Document chunk) {
        String text = chunk.getText();
        if (text == null || !text.startsWith(ENTRY_HEADER_PREFIX)) {
            return;
        }
        int headerEnd = text.indexOf(ENTRY_HEADER_SUFFIX);
        if (headerEnd < 0) {
            return;
        }
        String header = text.substring(ENTRY_HEADER_PREFIX.length(), headerEnd);
        String category = header.split(HEADER_FIELD_SEPARATOR, 2)[0].strip();
        if (!category.isEmpty()) {
            chunk.getMetadata().put(METADATA_CATEGORY, category);
        }
    }
}