package cn.wangtao.deepseek.harness.api.dto;

import java.util.List;

/**
 * 待回答用户问题条目。
 * <p>对应 {@code ask_user_question} 工具挂起的一次提问请求，前端据此渲染问答卡。</p>
 */
public record RuntimeQuestionResponseDTO(
        String questionId,
        String agentId,
        List<RuntimeQuestionItemDTO> questions,
        String createdAt
) {

    /**
     * 单个问题：题干、可选分组标题、选项列表与是否多选。
     */
    public record RuntimeQuestionItemDTO(
            String id,
            String question,
            String header,
            List<RuntimeQuestionOptionDTO> options,
            boolean multiSelect
    ) {}

    /**
     * 选项：短标签 + 一句权衡说明。
     */
    public record RuntimeQuestionOptionDTO(
            String label,
            String description
    ) {}
}
