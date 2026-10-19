package cn.wangtao.deepseek.harness.domain.model.valobj;

import java.util.List;

/**
 * 工具调用结果，会回传给模型。
 *
 * @param toolCallId 对应 {@link ToolCallBlock#id()}，模型据此把结果与调用配对。
 * @param content    结果内容块列表——工具可以只回文本（TextBlock），
 *                   也可以回图片等任意块，内容仍是 ContentBlock 家族，表达力不封顶。
 * @param isError    是否为失败结果；失败也会回填给模型，让模型看到错误后自行调整。
 * @author wangtao
 */
public record ToolResultBlock(
        String toolCallId,
        List<ContentBlock> content,
        boolean isError
) implements ContentBlock {
    public ToolResultBlock {
        content = List.copyOf(content);
    }
}
