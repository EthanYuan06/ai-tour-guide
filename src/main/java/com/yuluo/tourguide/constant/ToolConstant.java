package com.yuluo.tourguide.constant;

import lombok.experimental.UtilityClass;

/**
 * 工具调用链常量
 * 降级文案在此单源：工具失败必须回一句有明确语义的话，而不是抛异常终结对话
 */
@UtilityClass
public class ToolConstant {

    /**
     * 工具执行异常降级文案
     * 返回空串会让模型按通用知识编造具体数字，直接上抛则穿透 advisor 掐断整条 SSE 流；
     * 文案显式引导「换信息源或承认无法核实」，与提示词「一手记录保真」第 2 条同口径
     */
    public static final String FAILURE_HINT =
            "工具调用失败，未获取到任何结果；请改用其他信息源，或如实说明该信息暂时无法核实。";
}
