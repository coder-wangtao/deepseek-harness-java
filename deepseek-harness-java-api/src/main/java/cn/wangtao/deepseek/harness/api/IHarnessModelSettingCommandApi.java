package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.ActivateModelSettingRequestDTO;
import cn.wangtao.deepseek.harness.api.dto.ModelSettingResponseDTO;
import cn.wangtao.deepseek.harness.api.dto.SaveModelSettingRequestDTO;
import cn.wangtao.deepseek.harness.api.response.Response;

public interface IHarnessModelSettingCommandApi {

    Response<ModelSettingResponseDTO> save(SaveModelSettingRequestDTO request);

    /**
     * 删除模型设置。
     *
     */
    Response<Void> delete(String channelCode);

    /**
     * 将指定模型渠道切换为当前生效渠道。
     */
    Response<ModelSettingResponseDTO> activate(ActivateModelSettingRequestDTO request);
}
