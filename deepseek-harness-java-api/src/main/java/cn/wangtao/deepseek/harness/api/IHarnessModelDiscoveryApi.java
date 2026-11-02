package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.dto.DiscoverModelsRequestDTO;
import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.List;

/**
 * 模型发现接口：负责按渠道地址拉取上游可用模型列表。
 *
 * @author wt
 */
public interface IHarnessModelDiscoveryApi {

    /**
     * 根据渠道基础地址与协议探测上游可用模型，返回模型 ID 列表。
     *
     * @param request 上游模型同步请求，包含渠道基础地址（必填）、API Key（本地渠道可为空）与渠道协议（空值按 openai 兼容处理）
     * @return 上游可用模型 ID 列表
     */
    Response<List<String>> discover(DiscoverModelsRequestDTO request);
}
