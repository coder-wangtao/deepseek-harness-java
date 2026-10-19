package cn.wangtao.deepseek.harness.domain.model.valobj;

/**
 * 模型请求执行的一次工具调用。
 *
 * @param id        调用 ID（如 call_1），由模型生成；工具结果消息（ToolMessageSource）
 *                  靠它与本次调用对账。
 * @param name      要调用的工具名，须与注册表内注册的 ToolDefinition.name() 匹配。
 * @param arguments 参数 JSON 字符串。刻意保持 String 而非 Map：
 *                  该参数要原样透传到工具执行层，中途转 Map 再序列化回去
 *                  可能改变字段顺序/数字精度；原样持有、谁消费谁解析。
 * @author wangtao
 */
public record ToolCallBlock(String id, String name, String arguments) implements ContentBlock {}
