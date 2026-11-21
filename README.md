# AI 智能导游

基于 Spring AI 的 AI 旅游导览对话应用：提示词驱动的行程规划，叠加 RAG 检索增强（导游一手日本游语料），支持多轮流式对话与持久化记忆。

在线演示：[yuluo-ai.site](https://yuluo-ai.site)

## 功能

- SSE 流式多轮对话（`GET /api/chat/stream`），带 token 消耗统计
- 对话记忆持久化到 PostgreSQL（窗口 20 条，重启可恢复）
- RAG 检索增强（Agentic 形态）：检索管线封装为 `@Tool` `tripKnowledgeSearch`，由模型按需决定是否检索与检索什么，阈值 0.3 / topK 5；离线 ETL 由 `RegexTextSplitter` + `DocumentEtlUtils` 完成
- 会话管理：列表 / 历史懒加载 / 删除
- 前端 Vue3 单页聊天界面，Markdown + 代码高亮渲染，桌面 / 移动端响应式

规划中：需求复杂度路由、ReAct Agent、联网搜索工具、Supervisor 多 Agent 协同。

## 技术栈

- 后端：Java 21、Spring Boot 3.5、Spring AI 1.1（Spring AI Alibaba 提供 Embedding）
- 模型：DeepSeek（百炼 OpenAI 兼容模式接入）+ qwen text-embedding（1024 维）
- 数据：PostgreSQL 18 + pgvector（单库承载对话记忆、向量检索、业务表），MyBatis-Flex
- 前端：Vue 3 + TypeScript + Vite，原生 EventSource（SSE），markdown-it
- 部署：Docker Compose 一键编排（pgvector / backend / frontend 三服务）

## 架构图

```mermaid
graph TD
    %% 样式定义
    classDef startEnd fill:#2a9d8f,color:#fff,stroke:#264653,stroke-width:2px;
    classDef process fill:#457b9d,color:#fff,stroke:#1d3557,stroke-width:2px;
    classDef decision fill:#6a4c93,color:#fff,stroke:#432818,stroke-width:2px;
    classDef highlight fill:#e76f51,color:#fff,stroke:#333,stroke-width:2px;
    classDef expert fill:#3a5a40,color:#fff,stroke:#283618,stroke-width:2px;
    classDef enhancement fill:#f4a261,color:#000,stroke:#e76f51,stroke-width:2px;

    %% 1. 绝对顶端：入口节点
    Start([用户发送消息]):::startEnd
    IntentRouter{极轻量意图拦截<br>闲聊 vs 任务}:::decision
    Start --> IntentRouter

    %% ================= 2. 左侧旁路：闲聊分支 =================
    IntentRouter -- 闲聊/身份 --> DirectReply[闲聊直接回复<br>基于 System Prompt]:::process
    DirectReply --> EndCasual([流式回复用户]):::startEnd

    %% ================= 3. 垂直主干：需求分析 =================
    IntentRouter -- 任务/提问 --> ReqAnalysis{需求完备性分析<br>意图/目的地/天数/预算}:::decision

    %% 需求澄清 (作为左侧旁挂，避免拉扯主干垂直度)
    ReqAnalysis -- 不完备 --> Clarify["需求澄清节点<br>前期 MVP: 多轮对话引导<br>最终版: HITL 人工介入"]:::enhancement
    Clarify -- 补充完毕 --> ReqAnalysis

    ReqAnalysis -- 完备 --> Router{qwen3.8-flash 复杂度路由}:::decision

    %% ================= 4. 左下方：复杂规划链路 =================
    Router -- 复杂多步规划 --> Supervisor{主 Agent Supervisor<br>ReAct 模式·拆解·验收}:::decision

    subgraph ComplexFlow ["复杂行程规划链路 (多 Agent 协同)"]
        direction TB
        Supervisor -- 按需派发 --> WeatherAgent[天气专家 Agent]:::expert
        Supervisor -- 按需派发 --> MapAgent[地图专家 Agent]:::expert
        Supervisor -- 按需派发 --> SearchAgent[网页搜索专家 Agent]:::expert
        Supervisor == 强制派发 ==> DocAgent[文档生成 Agent]:::expert

        WeatherAgent -. 结果回传 .-> Supervisor
        MapAgent -. 结果回传 .-> Supervisor
        SearchAgent -. 结果回传 .-> Supervisor
        DocAgent -. 交付最终文档 .-> Supervisor
        
        Supervisor --> VisualComplex[前端流式可视化<br>思考链/工具状态]:::process
        VisualComplex --> EndComplex([流式回复用户]):::startEnd
    end

    %% ================= 5. 右下方：简单快问快答链路 =================
    Router -- 简单/单一事实 --> FastAgent[大模型综合处理<br>deepseek-chat]:::highlight

    subgraph SimpleFlow ["简单快问快答链路 (All-in-One 极简版)"]
        direction TB
        FastAgent -. 按需调用 .-> ToolWeather[实时天气 API]:::process
        FastAgent -. 按需调用 .-> ToolWeb[联网搜索 API]:::process
        FastAgent -. 按需检索 .-> KnowledgeRAG[RAG 旅游知识库]:::process
        
        FastAgent --> VisualSimple[前端流式可视化<br>简单链路状态]:::process
        VisualSimple --> EndSimple([流式回复用户]):::startEnd
    end
```

> 图示为目标架构：闲聊直答 + RAG 快问快答链路已上线，多 Agent 复杂规划链路为后续阶段规划。

## 思考

**为什么单库 pgvector，而不是 MySQL + 独立向量库？**
项目体量下记忆表、向量表、业务表合一库即可，启动自动建表免运维；已从 MySQL 切到 PG，MySQL 驱动仅保留回退。

**为什么用 pgvector，而不是专业向量库 / ES？**
79 条语料的小规模检索，PG 内 HNSW + COSINE 完全够用，不为向量单引入一套基础设施，也避免与记忆库跨库一致性问题。

**为什么选 Spring AI 而不是 LangChain 等框架？**
与 Spring Boot 同栈，ChatClient/Advisor 声明式编排、pgvector 与 chat-memory starter 开箱即用，Prompt 用 `.st` 模板管理，全程不引入 Python 异构。

**为什么 RAG 先做 Naive 形态，而不是直接上 Agent 检索？**
语料小而精、问题固定（三问 gold set），先用 `QuestionAnswerAdvisor` 一行挂上把链路跑通，用实测调出阈值 0.3 / topK 5 和保真规则；参数定稿后再把检索封装为 `@Tool`，升级为模型按需调用的 Agentic 形态。这样调参与能力演进分两步，黑盒阶段测出的参数在工具化后原样复用。

## 快速开始

```bash
# 1. 安装 Docker Desktop（含 docker compose）

# 2. 克隆仓库
git clone https://github.com/EthanYuan06/ai-tour-guide.git
cd ai-tour-guide

# 3. 在项目根目录创建 .env，填入 DASHSCOPE_API_KEY、PG_PASSWORD 等变量

# 4. 一键启动
./deploy.sh up
```

启动后访问 [http://localhost:18080](http://localhost:18080)；

接口文档 `http://localhost:8123/api/doc.html`
