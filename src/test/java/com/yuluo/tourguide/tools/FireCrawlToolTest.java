package com.yuluo.tourguide.tools;

import com.alibaba.cloud.ai.toolcalling.firecrawl.FireCrawlService;
import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Firecrawl 网页抓取工具的触网测试：正常抓取 / ToolCallback 包装链路 / 搜索-抓取串联 / 失败形态 / 工具元数据
 * 真实调用消耗 Firecrawl 配额，普通 mvn test 不执行；手动触发命令：
 * mvn test "-Dtest=FireCrawlToolTest" -DtoolsTest=true
 * .env 未配置对应 Key 时相关用例自动 skip，元数据用例不受 Key 影响
 */
@Slf4j
@SpringBootTest
@EnabledIfSystemProperty(named = "toolsTest", matches = "true")
class FireCrawlToolTest {

    private static final String HANABI_SITE = "https://hanabi.walkerplus.com/";

    private static final int MIN_CONTENT_LENGTH = 200;

    @Resource
    private FireCrawlService fireCrawlService;

    @Resource(name = "fireCrawlToolCallback")
    private ToolCallback fireCrawlToolCallback;

    @Resource
    private TavilySearchService tavilySearchService;

    @Value("${spring.ai.alibaba.toolcalling.firecrawl.api-key:}")
    private String firecrawlApiKey;

    @Value("${spring.ai.alibaba.toolcalling.tavilysearch.api-key:}")
    private String tavilyApiKey;

    @Test
    void normalScrapeReturnsPageContent() {
        requireFirecrawlKey();
        FireCrawlService.Response response = fireCrawlService.apply(
                new FireCrawlService.Request(HANABI_SITE));
        assertTrue(response != null && StringUtils.hasText(response.content()),
                "抓取返回空正文");
        assertTrue(response.content().length() >= MIN_CONTENT_LENGTH,
                "正文长度不足 " + MIN_CONTENT_LENGTH + "，实际 " + response.content().length());
        log.info("[firecrawl] {} 抓取 {} 字符，开头: {}", HANABI_SITE,
                response.content().length(), abbreviate(response.content()));
    }

    @Test
    void toolCallbackCallScrapesPage() {
        requireFirecrawlKey();
        // 专门验证 最小 inputSchema -> FireCrawlService.Request(url) 的反序列化路径
        String content = fireCrawlToolCallback.call("{\"url\":\"" + HANABI_SITE + "\"}");
        assertTrue(StringUtils.hasText(content), "ToolCallback 返回空");
        assertTrue(content.length() >= MIN_CONTENT_LENGTH,
                "ToolCallback 返回内容过短，实际 " + content.length() + " 字符");
        log.info("[firecrawl] ToolCallback 返回 {} 字符", content.length());
    }

    @Test
    void searchThenScrapeChain() {
        requireFirecrawlKey();
        Assumptions.assumeTrue(StringUtils.hasText(tavilyApiKey), "未配置 TAVILY_API_KEY，跳过串联用例");
        TavilySearchService.Response search = tavilySearchService.apply(
                TavilySearchService.Request.simpleQuery("日本 花火大会 会場 日程"));
        assertTrue(search != null && search.results() != null && !search.results().isEmpty(),
                "Tavily 前置搜索无结果");
        String targetUrl = search.results().get(0).url();
        log.info("[chain] Tavily 首条结果: {} | {}", search.results().get(0).title(), targetUrl);

        String content;
        try {
            content = fireCrawlService.apply(new FireCrawlService.Request(targetUrl)).content();
        } catch (Exception e) {
            // 部分站点反爬或超时，降级抓主站点首页（正常链路用例已验证可达）
            log.warn("[chain] 抓取搜索结果 {} 失败（{}），降级抓 {}", targetUrl, e.getMessage(), HANABI_SITE);
            content = fireCrawlService.apply(new FireCrawlService.Request(HANABI_SITE)).content();
        }
        assertTrue(StringUtils.hasText(content) && content.length() >= MIN_CONTENT_LENGTH,
                "串联抓取正文为空或过短");
        log.info("[chain] 抓取成功，正文 {} 字符", content.length());
    }

    @Test
    void invalidUrlFailsGracefully() {
        requireFirecrawlKey();
        assertFailureSignal("无效 URL", () -> fireCrawlService.apply(
                new FireCrawlService.Request("这不是一个URL")));
    }

    @Test
    void unreachableDomainFailsGracefully() {
        requireFirecrawlKey();
        assertFailureSignal("不可达域名", () -> fireCrawlService.apply(
                new FireCrawlService.Request("https://nonexistent-domain-test.invalid/")));
    }

    @Test
    void toolDefinitionMetadata() {
        ToolDefinition definition = fireCrawlToolCallback.getToolDefinition();
        assertEquals("fireCrawl", definition.name());
        assertTrue(definition.description().contains("网页抓取"),
                "description 不符合预期: " + definition.description());
        assertTrue(definition.inputSchema().contains("\"url\""), "inputSchema 缺少 url 字段");
        assertTrue(definition.inputSchema().contains("required"), "inputSchema 未声明 required");
    }

    /**
     * 失败形态断言：抛异常或返回空/错误正文均可，静默返回整页内容才判失败
     */
    private void assertFailureSignal(String caseName, java.util.function.Supplier<FireCrawlService.Response> call) {
        try {
            FireCrawlService.Response response = call.get();
            String content = response == null ? null : response.content();
            log.info("[firecrawl] {} 实际行为: 返回 content={}", caseName, abbreviate(content));
            boolean graceful = content == null
                    || content.isBlank()
                    || content.toLowerCase().contains("error")
                    || content.length() < MIN_CONTENT_LENGTH;
            assertTrue(graceful, caseName + " 却静默返回了整页内容: " + abbreviate(content));
        } catch (Exception e) {
            log.info("[firecrawl] {} 抛出异常（优雅失败）: {}", caseName, e.getMessage());
        }
    }

    private void requireFirecrawlKey() {
        Assumptions.assumeTrue(StringUtils.hasText(firecrawlApiKey), "未配置 FIRECRAWL_API_KEY，跳过触网用例");
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }
}
