package cn.wangtao.deepseek.harness.domain.model.valobj;

import java.util.Map;

/**
 * 发送给模型的工具 JSON Schema 描述。
 * <p>
 * Mirrors TS {@code ToolSchema}.
 *
 * @param name        工具名称
 * @param description 工具描述
 * @param parameters  参数对应的 JSON Schema 对象（Map 形式，非强类型）
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 * @author wangtao
 */
public record ToolSchema(
        String name,
        String description,
        Map<String, Object> parameters
) {
    public ToolSchema {
        //参数为 null 时抛出 NullPointerException，异常消息就是第二个参数，
        //比如 "name"，便于定位是哪个字段出了问题。不为 null 就什么也不做。
        java.util.Objects.requireNonNull(name, "name");
        java.util.Objects.requireNonNull(description, "description");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
