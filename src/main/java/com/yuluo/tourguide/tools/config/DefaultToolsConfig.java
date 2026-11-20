package com.yuluo.tourguide.tools.config;

import com.alibaba.cloud.ai.toolcalling.firecrawl.FireCrawlService;
import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * Spring AI Alibaba 已集成工具的接入配置：tool-calling starter 自动装配产出的是
 * Function 服务 Bean 而非 ToolCallback，这里显式包装成 ToolCallback Bean 供 ChatClient 挂载。
 * 本类只服务于官方已集成的工具，项目自定义工具（PDF 生成、浏览器下载等）由 ToolRegistration 集中注册
 */
@Configuration
public class DefaultToolsConfig {

    /**
     * Tavily 原生 Request 有十余个字段，全部暴露会干扰模型参数生成，schema 只保留必填关键词
     */
    private static final String TAVILY_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "联网检索关键词"
                }
              },
              "required": ["query"]
            }
            """;

    private static final String FIRECRAWL_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "url": {
                  "type": "string",
                  "description": "要抓取正文的目标网页 URL"
                }
              },
              "required": ["url"]
            }
            """;

    /**
     * Tavily 工具的最小入参，仅承载模型生成的检索关键词
     */
    public record TavilySearchInput(String query) {
    }

    @Bean
    public ToolCallback tavilySearchToolCallback(TavilySearchService tavilySearchService) {
        // Tavily 原生 Request 含原始 boolean 字段，模型回传的部分 JSON 直接反序列化到它会失败，
        // 故经官方 simpleQuery 工厂转成完整 Request
        Function<TavilySearchInput, TavilySearchService.Response> search =
                input -> tavilySearchService.apply(TavilySearchService.Request.simpleQuery(input.query()));
        return FunctionToolCallback.builder("tavilySearch", search)
                .description("联网搜索工具：当问题涉及时效性信息、最新价格、开放时间与票务、行程攻略等本地知识库未覆盖的内容时调用，输入检索关键词，返回网页搜索结果摘要与来源链接。")
                .inputType(TavilySearchInput.class)
                .inputSchema(TAVILY_INPUT_SCHEMA)
                .build();
    }

    @Bean
    public ToolCallback fireCrawlToolCallback(FireCrawlService fireCrawlService) {
        return FunctionToolCallback.builder("fireCrawl", fireCrawlService)
                .description("网页抓取工具：当需要读取某个具体网页 URL 的正文内容（如景点官网、票价详情页、行程页面）以获取更完整信息时调用，输入目标网页 url，返回该页面正文。")
                .inputType(FireCrawlService.Request.class)
                .inputSchema(FIRECRAWL_INPUT_SCHEMA)
                .build();
    }
}
