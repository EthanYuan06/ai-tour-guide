package com.yuluo.tourguide.tools;

import com.yuluo.tourguide.constant.RagConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 日本游一手记录检索工具：把向量检索暴露为 @Tool，由模型按需调用，构成 Agentic RAG
 * description 是唯一的调用路由器——查询重写由模型生成入参时天然完成，上下文拼装由框架回填工具结果消息代劳，
 * 故无需 QueryTransformer 与 Augmentation 模块
 */
@Slf4j
@Component
public class TripKnowledgeSearchTool {

    @Resource
    private VectorStore vectorStore;

    @Tool(name = RagConstant.TOOL_NAME, description = """
            检索导游本人的日本游一手记录，内容仅覆盖：2025 年 7 月日本行程的实际消费明细与金额（日元/人民币）、
            交通花费、住宿分摊口径、景点与线路体验、日本短期观光签证的材料与办理要求。
            以下情况必须调用：问题涉及具体金额、汇率、行程天数、线路安排、签证材料与资产要求；
            即使用户是在追问你上一轮已回答过的内容，只要要的是其中的具体数字或细节，也必须重新调用核实，
            不得凭上文回复直接作答。
            以下情况不要调用：闲聊、情绪交流、通用旅游常识，以及实时票价、开放时间等时效性信息（应改用联网搜索工具）。
            """)
    public String searchTripRecords(
            @ToolParam(description = "检索用的完整问题，须是把上下文补全后的自包含表述，"
                    + "不要使用「那个」「上面提到的」等指代") String question) {
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .similarityThreshold(RagConstant.SIMILARITY_THRESHOLD)
                    .topK(RagConstant.TOP_K)
                    .build());
            if (documents == null || documents.isEmpty()) {
                return RagConstant.EMPTY_RESULT_HINT;
            }
            // 断点建议落在上一行：入参 question 即模型自行补全后的查询，documents 即召回切片与分数
            log.info("[rag] question={} hits={} scores={}", question, documents.size(), formatScores(documents));
            return documents.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.warn("[rag] 知识库检索异常 question={}", question, e);
            return RagConstant.FAILURE_HINT;
        }
    }

    /**
     * Document#getScore 返回 Double 且可能为 null（部分 VectorStore 实现不回填分数），
     * 直接 %.2f 会 NPE 或输出 "null"，故逐个判空
     * 包级可见而非 private：让单测直接断言两位小数格式化结果，不必通过日志文本断言（脆弱且无价值）
     */
    String formatScores(List<Document> documents) {
        return documents.stream()
                .map(d -> d.getScore() == null ? "-" : String.format("%.2f", d.getScore()))
                .collect(Collectors.joining(","));
    }
}
