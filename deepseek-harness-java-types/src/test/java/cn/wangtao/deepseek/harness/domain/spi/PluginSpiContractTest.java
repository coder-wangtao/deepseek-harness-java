package cn.wangtao.deepseek.harness.domain.spi;

import cn.wangtao.deepseek.harness.domain.model.entity.AbstractTool;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * xfg-1-2 验证入口：插件 SPI 契约的最小行为验证。
 *
 * <p>覆盖四件事：
 * 1) PluginManifest 清单解析与校验；
 * 2) 生命周期状态机流转；
 * 3) PluginContext 假实现完成一次工具注册（记录注册顺序，模拟宿主逆序回收）；
 * 4) PluginHook 的 block/allow 决策与 PluginEvent 不可变信封。</p>
 */
class PluginSpiContractTest {

    /** 测试用工具：一加一。 */
    static final class OnePlusOneTool extends AbstractTool {
        @Override public String name() { return "one_plus_one"; }
        @Override public String description() { return "Returns 2."; }
        @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok("2");
        }
    }

    /** 插件假实现：用列表记录 register* 调用顺序，模拟宿主回收验证。 */
    static final class RecordingPluginContext implements PluginContext {
        final List<String> registered = new java.util.ArrayList<>();
        final String pluginId;

        RecordingPluginContext(String pluginId) { this.pluginId = pluginId; }

        @Override public String pluginId() { return pluginId; }
        @Override public AutoCloseable registerTool(ToolDefinition tool) {
            registered.add("tool:" + tool.name());
            return () -> registered.add("dispose:tool:" + tool.name());
        }
        @Override public void registerDisposer(AutoCloseable disposer) { registered.add("disposer"); }
        @Override public AutoCloseable subscribe(String eventName, PluginEventHandler handler) {
            registered.add("subscribe:" + eventName);
            return () -> {};
        }
        @Override public void emit(String eventName, Object payload) { registered.add("emit:" + eventName); }
        @Override public AutoCloseable registerHook(String hookPoint, PluginHook hook) {
            registered.add("hook:" + hookPoint);
            return () -> {};
        }
        @Override public Optional<String> getConfig(String key) { return Optional.empty(); }
        @Override public AutoCloseable registerSystemPrompt(String name, int order, String text) {
            registered.add("prompt:" + name);
            return () -> {};
        }
    }

    @Test
    void manifest_requiresIdNameVersion() {
        PluginManifest manifest = new PluginManifest("time", "Time Plugin", "1.0.0",
                null, null, "com.example.TimePlugin");
        assertEquals("time", manifest.id());
        assertEquals("com.example.TimePlugin", manifest.entrypoint());
        // id 为空必须拒绝
        assertThrows(NullPointerException.class,
                () -> new PluginManifest(null, "x", "1.0.0", null, null, null));
    }

    @Test
    void lifecycleState_coversAllPhases() {
        // 状态机：PENDING → LOADING → ACTIVE → UNLOADING → DISPOSED，失败进 FAILED
        assertEquals(6, PluginLifecycleState.values().length);
        assertEquals("ACTIVE", PluginLifecycleState.ACTIVE.name());
    }

    @Test
    void pluginConfigure_registersToolWithHostPrefixOrder() {
        // 模拟宿主加载：configure() 把 tools() 注册进 context
        JavaHarnessPlugin plugin = new JavaHarnessPlugin() {
            @Override public String pluginId() { return "time"; }
            @Override public List<ToolDefinition> tools() { return List.of(new OnePlusOneTool()); }
        };
        RecordingPluginContext context = new RecordingPluginContext("time");
        plugin.configure(context);

        assertEquals(List.of("tool:one_plus_one"), context.registered);
        // 默认 configure() 实现按 tools() 顺序注册，与原 SPI 兼容
    }

    @Test
    void hookBlock_resultCarriesDecisionAndReason() {
        PluginHookResult blocked = PluginHookResult.block("禁止删除 /etc");
        assertEquals("BLOCK", blocked.decision().orElse(""));
        assertEquals("禁止删除 /etc", blocked.reason().orElse(""));

        PluginHookResult allowed = PluginHookResult.allow();
        assertEquals("ALLOW", allowed.decision().orElse(""));

        // 无参构造的四个字段都是空 Optional（Hook 可以"不表达意见"）
        PluginHookResult neutral = new PluginHookResult(null, null, null, null);
        assertTrue(neutral.decision().isEmpty());
    }

    @Test
    void eventEnvelope_immutableAndTimestamped() {
        PluginEvent event = new PluginEvent("host", "plugin.started", "time", null);
        assertEquals("host", event.source());
        assertEquals("plugin.started", event.name());
        assertNotNull(event.timestamp()); // timestamp 缺省自动补当前时间
        assertThrows(IllegalArgumentException.class, () -> new PluginEvent("", "x", null, null));
    }
}
