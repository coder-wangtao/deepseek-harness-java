package cn.wangtao.deepseek.harness.api.dto;

import java.util.List;

/**
 * 提交用户回答请求体。
 * <p>每个回答对应一个问题 id：selected 为勾选的选项标签（可多个），
 * custom 为自由文本回答；两者至少其一。</p>
 */
public record SubmitUserAnswerRequestDTO(
        List<AnswerItemDTO> answers
) {

    public record AnswerItemDTO(
            String id,
            List<String> selected,
            String custom
    ) {}
}
