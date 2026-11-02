package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.RuntimeQuestionResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.SubmitUserAnswerRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.SubmitUserAnswerResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 运行期用户问答 API：查询 {@code ask_user_question} 挂起的待回答问题，并提交用户回答。
 */
public interface IRuntimeQuestionApi {

    Response<List<RuntimeQuestionResponseDTO>> listPending();

    Response<SubmitUserAnswerResponseDTO> submitAnswer(
            String questionId,
            SubmitUserAnswerRequestDTO request
    );
}
