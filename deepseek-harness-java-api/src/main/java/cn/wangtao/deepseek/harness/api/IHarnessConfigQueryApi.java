package cn.wangtao.deepseek.harness.api;

import cn.wangtao.deepseek.harness.api.response.Response;
import java.util.Map;

/**
 * API for reading the effective harness configuration.
 */
public interface IHarnessConfigQueryApi {

    Response<Map<String, Object>> effectiveConfig();
}
