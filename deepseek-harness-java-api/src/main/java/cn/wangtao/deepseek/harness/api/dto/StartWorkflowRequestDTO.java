package cn.wangtao.deepseek.harness.api.dto;

import java.util.Map;

/**
 * 工作流启动请求。
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 * @author wt
 *
 * @param script 工作流脚本内容
 * @param name 工作流名称
 * @param parentCwd 工作流执行的父级工作目录
 * @param args 启动参数，键值对形式
 */
public record StartWorkflowRequestDTO(
        String script,
        String name,
        String parentCwd,
        Map<String, Object> args
) {
}
