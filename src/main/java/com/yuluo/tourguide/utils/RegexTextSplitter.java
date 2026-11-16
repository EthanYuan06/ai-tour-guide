package com.yuluo.tourguide.utils;

import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 通用正则切分器：按正则边界把文本切成片段，超长片段按字符数硬切兜底（无重叠）
 * 继承 TextSplitter 以复用基类的切片 metadata 复制能力
 */
public class RegexTextSplitter extends TextSplitter {

    private final Pattern pattern;

    private final int maxChunkLength;

    /**
     * @param regex          切分边界正则，推荐零宽断言（如 (?=【)）以保留边界字符
     * @param maxChunkLength 单片最大字符数，<=0 表示不限制
     */
    public RegexTextSplitter(String regex, int maxChunkLength) {
        this.pattern = Pattern.compile(regex);
        this.maxChunkLength = maxChunkLength;
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        for (String part : pattern.split(text)) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (maxChunkLength <= 0 || trimmed.length() <= maxChunkLength) {
                chunks.add(trimmed);
            } else {
                for (int i = 0; i < trimmed.length(); i += maxChunkLength) {
                    chunks.add(trimmed.substring(i, Math.min(i + maxChunkLength, trimmed.length())));
                }
            }
        }
        return chunks;
    }
}