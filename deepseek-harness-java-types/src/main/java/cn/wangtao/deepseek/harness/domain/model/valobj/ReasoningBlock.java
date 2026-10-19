package cn.wangtao.deepseek.harness.domain.model.valobj;

/**
 * 推理/思考内容块，与可见文本（{@link TextBlock}）相互区分。
 * <p>用途：承载 DeepSeek-R1 等推理模型返回的思维链（reasoning_content）。
 * 因为与"说给用户听的话"语义不同，前端可独立控制展示/折叠，
 * 持久化时可选择是否落库。</p>
 * @author wangtao
 */
public record ReasoningBlock(String text) implements ContentBlock {}
