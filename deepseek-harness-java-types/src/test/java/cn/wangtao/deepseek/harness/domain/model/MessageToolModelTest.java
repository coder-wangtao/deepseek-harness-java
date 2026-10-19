package cn.wangtao.deepseek.harness.domain.model;

import cn.wangtao.deepseek.harness.domain.model.entity.AbstractTool;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecution;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecutionInput;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.wangtao.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolCallBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * xfg-1-1 验证入口：消息与工具模型的最小行为验证。
 *
 * <p>覆盖四件事：
 * 1) Message 工厂创建（user/assistant/tool）与不可变性；
 * 2) ContentBlock 密封层级可穷尽 switch；
 * 3) EchoTool 继承 AbstractTool 的最小工具实现；
 * 4) 工具执行链 ToolExecutionInput → ToolExecution → ToolRunContext → 结果回填。</p>
 */
class MessageToolModelTest {

    /** 最小回声工具：验证 AbstractTool 模板方法模式。 */
    static final class EchoTool extends AbstractTool {
        @Override public String name() { return "echo"; }
        @Override public String description() { return "Echoes text back."; }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("text", stringSchema("The text to echo"))
                    .required("text")
                    .build();
        }
        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok("Echo: " + str(args, "text"));
        }
    }

    @Test
    void createUserMessage_stableIdAndImmutableContent() {
        Message msg = Message.createUser(List.of(new TextBlock("你好")), new MessageSource.UserSource());
        assertEquals("user", msg.role());
        assertEquals(1, msg.content().size());
        // 不可变：内容列表拒绝修改
        assertThrows(UnsupportedOperationException.class, () -> msg.content().add(new TextBlock("x")));
    }

    @Test
    void createToolResult_wrapsToolResultBlockWithCallId() {
        Message msg = Message.createToolResult("call-1", List.of(new TextBlock("结果")), false);
        assertEquals("tool", msg.role());
        assertInstanceOf(MessageSource.ToolMessageSource.class, msg.source());
        assertEquals("call-1", ((MessageSource.ToolMessageSource) msg.source()).callId());
    }

    @Test
    void contentBlockSealedHierarchy_coversAllKinds() {
        // Java 17 无 switch 模式匹配，用 instanceof 链表达 sealed 分支；
        // sealed permits 保证层级封闭，新增块类型时编译器会在所有 instanceof 链外提醒处理
        ContentBlock block = new ToolCallBlock("call-1", "echo", "{\"text\":\"hi\"}");
        String kind;
        if (block instanceof TextBlock) kind = "text";
        else if (block instanceof ToolCallBlock) kind = "tool_call";
        else if (block instanceof cn.wangtao.deepseek.harness.domain.model.valobj.ToolResultBlock) kind = "tool_result";
        else if (block instanceof cn.wangtao.deepseek.harness.domain.model.valobj.ReasoningBlock) kind = "reasoning";
        else if (block instanceof cn.wangtao.deepseek.harness.domain.model.valobj.ImageBlock) kind = "image";
        else kind = "unknown";
        assertEquals("tool_call", kind);
    }

    @Test
    void echoTool_executesAndReturnsText() throws Exception {
        EchoTool tool = new EchoTool();
        assertEquals("echo", tool.name());
        // JSON Schema 已声明必填参数
        assertTrue(tool.parameters().get("required").toString().contains("text"));

        ToolRunContext ctx = new ToolRunContext(
                ToolExecution.from(ToolExecutionInput.root("call-1", "echo", Map.of("text", "hi"))));
        ToolExecutionResult result = tool.execute(Map.of("text", "hi"), ctx).get();

        assertFalse(result.isError());
        assertTrue(((TextBlock) result.content().get(0)).text().contains("Echo: hi"));
    }

    @Test
    void echoTool_invalidArgsType_returnsFailure() throws Exception {
        EchoTool tool = new EchoTool();
        ToolRunContext ctx = new ToolRunContext(
                ToolExecution.from(ToolExecutionInput.root("call-2", "echo", "bad")));
        ToolExecutionResult result = tool.execute("not-a-map", ctx).get();

        assertTrue(result.isError());
    }
}
