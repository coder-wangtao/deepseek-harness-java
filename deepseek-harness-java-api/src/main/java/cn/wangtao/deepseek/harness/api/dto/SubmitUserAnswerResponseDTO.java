package cn.wangtao.deepseek.harness.api.dto;

/**
 * 提交用户回答结果。
 * <p>submitted=true 表示回答已送达挂起的 Agent；否则附 error 说明（如问题已超时）。</p>
 */
public record SubmitUserAnswerResponseDTO(
        boolean submitted,
        String error
) {}
