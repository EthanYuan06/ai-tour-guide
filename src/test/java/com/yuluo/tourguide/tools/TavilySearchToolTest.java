package com.yuluo.tourguide.tools;

import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tavily 联网搜索工具的触网测试：正常检索 / ToolCallback 包装链路 / 边界形态 / 工具元数据
 * 真实调用消耗 Tavily 配额，普通 mvn test 不执行；手动触发命令：
 * mvn test "-Dtest=TavilySearchToolTest" -DtoolsTest=true
 * .env 未配置 TAVILY_API_KEY 时联网用例自动 skip，元数据用例不受 Key 影响
 */
@Slf4j
@SpringBootTest
@EnabledIfSystemProperty(named = "toolsTest", matches = "true")
class TavilySearchToolTest {

    private static final String MAIN_QUERY = "日本 花火大会 2026 開催日程 会場";

    @Resource
    private TavilySearchService tavilySearchService;

    @Resource(name = "tavilySearchToolCallback")
    private ToolCallback tavilySearchToolCallback;

    @Value("${spring.ai.alibaba.toolcalling.tavilysearch.api-key:}")
    private String tavilyApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalSearchReturnsResultList() {
        requireApiKey();
        TavilySearchService.Response response = tavilySearchService.apply(
                TavilySearchService.Request.simpleQuery(MAIN_QUERY));
        assertNotNull(response, "Tavily 返回 null");
        assertNotNull(response.results(), "响应缺少 results 节点");
        assertFalse(response.results().isEmpty(), "results 为空");
        for (TavilySearchService.Response.ResultInfo result : response.results()) {
            assertTrue(StringUtils.hasText(result.url()), "结果缺少 url: " + result);
            assertTrue(StringUtils.hasText(result.title()), "结果缺少 title: " + result);
        }
        log.info("[tavily] 「{}」命中 {} 条，answer={}", MAIN_QUERY, response.results().size(),
                abbreviate(response.answer()));
        response.results().stream().limit(3).forEach(r ->
                log.info("[tavily]   score={} | {} | {}", r.score(), r.title(), r.url()));
    }

    @Test
    void toolCallbackCallDeserializesMinimalInput() throws Exception {
        requireApiKey();
        // 专门验证 最小 inputSchema -> TavilySearchInput -> simpleQuery 的适配链路
        String json = tavilySearchToolCallback.call(
                "{\"query\":\"日本近期花火大会 开放时间 举办地点\"}");
        assertTrue(StringUtils.hasText(json), "ToolCallback 返回空");
        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("results"), "返回值不是含 results 节点的 Response JSON: " + abbreviate(json));
        log.info("[tavily] ToolCallback 返回 {} 字符，results 命中 {} 条",
                json.length(), root.path("results").size());
    }

    @Test
    void emptyQueryFailsGracefully() {
        requireApiKey();
        try {
            TavilySearchService.Response response = tavilySearchService.apply(
                    TavilySearchService.Request.simpleQuery(""));
            log.info("[tavily] 空查询实际行为: results={}",
                    response == null ? "null" : (response.results() == null ? "null" : response.results().size() + " 条"));
            assertTrue(response == null
                            || response.results() == null
                            || response.results().isEmpty()
                            || (response.answer() != null && response.answer().isBlank()),
                    "空查询返回了带内容的结果，需确认是否为假成功: " + abbreviate(response.toString()));
        } catch (Exception e) {
            log.info("[tavily] 空查询抛出异常（优雅失败）: {}", e.getMessage());
        }
    }

    @Test
    void garbageQueryDoesNotCrash() {
        requireApiKey();
        assertDoesNotThrow(() -> {
            TavilySearchService.Response response = tavilySearchService.apply(
                    TavilySearchService.Request.simpleQuery("asdfghjkl中文混排!?#"));
            log.info("[tavily] 无意义字符串命中 {} 条",
                    response == null || response.results() == null ? 0 : response.results().size());
        });
    }

    @Test
    void toolDefinitionMetadata() {
        ToolDefinition definition = tavilySearchToolCallback.getToolDefinition();
        assertEquals("tavilySearch", definition.name());
        assertTrue(definition.description().contains("联网搜索"),
                "description 不符合预期: " + definition.description());
        assertTrue(definition.inputSchema().contains("\"query\""), "inputSchema 缺少 query 字段");
        assertTrue(definition.inputSchema().contains("required"), "inputSchema 未声明 required");
    }

    private void requireApiKey() {
        Assumptions.assumeTrue(StringUtils.hasText(tavilyApiKey), "未配置 TAVILY_API_KEY，跳过触网用例");
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
