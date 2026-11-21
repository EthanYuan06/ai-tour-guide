package com.yuluo.tourguide.constant;

import lombok.experimental.UtilityClass;

/**
 * RAG 检索口径常量：阈值、召回数与降级文案的唯一来源
 * 阈值与 topK 沿用 QuestionAnswerAdvisor 形态下阶段二黑盒验证的实测调参结果，工具化不改检索口径
 */
@UtilityClass
public class RagConstant {

    /**
     * 检索工具名，与 @Tool(name = ...) 同源
     * 单测断言工具元数据、日志核对工具挂载列表时引用此处，避免字符串在两处漂移
     */
    public static final String TOOL_NAME = "tripKnowledgeSearch";

    /**
     * 相似度阈值：低于此值的切片视为不相关而丢弃
     * 云端 pgvector 实测一手语料的命中分数集中在 0.45-0.65，0.3 留了足够余量给改写后的问法
     */
    public static final double SIMILARITY_THRESHOLD = 0.3d;

    /**
     * 单轮召回切片数上限
     */
    public static final int TOP_K = 5;

    /**
     * 空命中降级文案：必须回一句有明确语义的话
     * 返回空串会让模型误判为「查过了但库里没有」，转而用通用知识编造具体金额
     */
    public static final String EMPTY_RESULT_HINT = "知识库中未检索到与该问题相关的一手记录。";

    /**
     * 检索异常降级文案
     * 查询向量化需走 DashScope 网络请求，超时是常态；降级只损失这一路信息源，不应中断整轮对话
     */
    public static final String FAILURE_HINT = "知识库检索暂时不可用。";
}
