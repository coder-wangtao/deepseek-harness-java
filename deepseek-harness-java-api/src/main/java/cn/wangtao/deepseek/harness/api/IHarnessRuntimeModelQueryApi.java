package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.AvailableModelResponseDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 运行时模型查询接口：负责查询当前 LLM 运行时可用的模型渠道列表。
 *
 * @author wt
 */
public interface IHarnessRuntimeModelQueryApi {

    /**
     * 查询当前 LLM 运行时可用的全部模型渠道。
     *
     * @return 可用模型渠道列表，每项包含渠道编码、显示名称、供应商编码与模型编码
     */
    Response<List<AvailableModelResponseDTO>> queryAll();
}
