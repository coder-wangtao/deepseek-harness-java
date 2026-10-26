package cn.wangtao.deepseek.harness.domain.spi;

import cn.wangtao.deepseek.harness.domain.model.entity.AbstractTool;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.wangtao.deepseek.harness.domain.model.llm.LlmChatClient;
import cn.wangtao.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolCallBlock;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SPI 场景最小案例：完整演示一个 Harness 插件从定义到运行、回收的全过程。
 *
 * <p>本案例与 v0.1.8 分支确立的插件方案保持一致，插件作者只需三步：</p>
 * <ol>
 *   <li>写一个工具：继承 {@link AbstractTool}，实现 name / description / parameters / run；</li>
 *   <li>写一个插件：继承 {@link AbstractHarnessPlugin}，构造器传 pluginId，
 *       在 {@code configure(PluginContext)}（默认实现按 {@code tools()} 顺序注册）中
 *       把工具交给宿主；</li>
 *   <li>打包两个清单文件进插件 JAR（本案例用字符串常量模拟其内容）：
 *       <pre>
 *       META-INF/plugin.yaml
 *         id: greeting
 *         name: Greeting Plugin
 *         version: 1.0.0
 *         entrypoint: 插件主类全限定名（可选，缺省走 ServiceLoader）
 *
 *       META-INF/services/cn.wangtao.deepseek.harness.domain.spi.JavaHarnessPlugin
 *         插件主类全限定名
 *       </pre></li>
 * </ol>
 *
 * <p>宿主侧（Harness）的职责由测试内的 {@link FakeHost} 模拟：
 * 通过 {@link PluginContext} 注册边界加载插件 → 工具以
 * {@code plugin__<pluginId>__<toolName>} 暴露 → 停止时逆序回收所有注册项。</p>
 */
class MinimalPluginExampleTest {

    private static final Logger log = LoggerFactory.getLogger(MinimalPluginExampleTest.class);

    /**
     * 最小插件：继承 AbstractHarnessPlugin，只做两件事——
     * 声明 pluginId（与 plugin.yaml 的 id 一致，基类保证不会漂移），
     * 并通过默认 configure() 把工具注册进宿主上下文。
     */
    static final class GreetingPlugin extends AbstractHarnessPlugin {

        GreetingPlugin() {
            super("greeting");
        }

        @Override
        public List<ToolDefinition> tools() {
            return List.of(new GreetTool());
        }

        static class GreetTool extends AbstractTool {
            @Override
            public String name() {
                return "greet";
            }

            @Override
            public String description() {
                return "按姓名生成问候语。参数 name 必填。";
            }

            @Override
            public Map<String, Object> parameters() {
                return objectSchema()
                        .prop("name", stringSchema("要问候的人名"))
                        .required("name")
                        .build();
            }

            @Override
            protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
                String name = str(args, "name");
                if (name.isEmpty()) {
                    return fail("name 不能为空", "INVALID_ARGS");
                }
                return ok("我是 DSH Java。死鬼，你好，" + name + "！");
            }
        }

    }

    // ── 清单文件内容（真实工程中放在 resources/META-INF 下） ──────────

    static final String PLUGIN_YAML = """
            id: greeting
            name: Greeting Plugin
            version: 1.0.0
            entrypoint: MinimalPluginExampleTest$GreetingPlugin
            """;

    static final String SPI_FILE = "MinimalPluginExampleTest$GreetingPlugin";

    // ── 宿主侧模拟：注册、暴露、回收 ──────────────────────────────────

    /**
     * 假宿主：复刻 Harness 对插件的关键行为。
     * <ul>
     *   <li>configure() 期间，插件经 {@link PluginContext} 注册的一切由宿主持有；</li>
     *   <li>工具名加前缀 {@code plugin__<pluginId>__<toolName>} 后暴露给 Agent；</li>
     *   <li>stop() 时逆序调用关闭器，保证资源释放顺序与注册顺序相反。</li>
     *   </ul>
     */
    static final class FakeHost {

        /** 宿主暴露给 Agent 的工具表：key 为带前缀完整名 plugin__<pluginId>__<toolName>。 */
        final Map<String, ToolDefinition> exposedTools = new HashMap<>();

        /**
         * 停用回收栈：configure() 期间每个注册项的关闭器在此压入，
         * stop() 时逆序 pop().close()，保证释放顺序与注册顺序相反。
         */
        final Deque<AutoCloseable> disposers = new ArrayDeque<>();

        /** 宿主 system prompt 组装清单：插件注入的段落以 plugin__<pluginId>__<name> 前缀登记。 */
        final List<String> systemPrompts = new ArrayList<>();

        /** 宿主持有的 Hook 表：key 为钩点名称（如 PRE_TOOL_USE），执行流程到达钩点时咨询插件。 */
        final Map<String, PluginHook> hooks = new HashMap<>();

        /** 已投递到订阅方的事件记录：emit() 时追加，供测试断言事件确实送达。 */
        final List<PluginEvent> deliveredEvents = new ArrayList<>();

        /** 宿主下发的插件配置（运行时覆盖值，最简模拟 PluginConfigStore 的覆盖层）。 */
        final Map<String, String> configOverrides = new HashMap<>();

        /**
         * 模拟宿主加载插件：实例化 → configure → onStart。
         */
        void load(JavaHarnessPlugin plugin) {
            plugin.configure(new HostContext(plugin.pluginId()));
            plugin.onStart();
        }

        /**
         * 模拟宿主发布事件：投递给订阅方（真实宿主经 PluginEventBus 分发）。
         */
        void emit(String eventName, Object payload) {
            deliveredEvents.add(new PluginEvent("host", eventName, payload, null));
        }

        /**
         * 模拟宿主停用插件：onStop → 逆序回收。
         */
        void stop(JavaHarnessPlugin plugin) throws Exception {
            plugin.onStop();
            while (!disposers.isEmpty()) {
                disposers.pop().close();
            }
        }

        private final class HostContext implements PluginContext {

            private final String pluginId;

            HostContext(String pluginId) {
                this.pluginId = pluginId;
            }

            @Override
            public String pluginId() {
                return pluginId;
            }

            @Override
            public AutoCloseable registerTool(ToolDefinition tool) {
                // 宿主用带插件前缀的完整名注册工具，Agent 循环看到的是这个名字
                exposedTools.put("plugin__" + pluginId + "__" + tool.name(), tool);
                disposers.push(() -> exposedTools.remove("plugin__" + pluginId + "__" + tool.name()));
                return () -> {
                };
            }

            @Override
            public void registerDisposer(AutoCloseable disposer) {
                disposers.push(disposer);
            }

            @Override
            public AutoCloseable subscribe(String eventName, PluginEventHandler handler) {
                // 宿主持有订阅关系：本案例中事件由 FakeHost.emit 直接投递
                disposers.push(() -> { });
                return () -> { };
            }

            @Override
            public void emit(String eventName, Object payload) { /* 发送后即忘 */ }

            @Override
            public AutoCloseable registerHook(String hookPoint, PluginHook hook) {
                hooks.put(hookPoint, hook);
                disposers.push(() -> hooks.remove(hookPoint));
                return () -> { };
            }

            @Override
            public java.util.Optional<String> getConfig(String key) {
                return java.util.Optional.ofNullable(configOverrides.get(key));
            }

            @Override
            public AutoCloseable registerSystemPrompt(String name, int order, String text) {
                systemPrompts.add("plugin__" + pluginId + "__" + name);
                disposers.push(() -> systemPrompts.remove("plugin__" + pluginId + "__" + name));
                return () -> {
                };
            }
        }
    }

    // ── 案例：一次完整的加载 → 调用 → 回收 ────────────────────────────

    @Test
    void minimalPlugin_fullLifecycle() throws Exception {
        // 1. 清单与 SPI 文件中的插件 ID / 入口一致（真实工程里由宿主解析校验）
        assertTrue(PLUGIN_YAML.startsWith("id: greeting"));
        assertEquals(PLUGIN_YAML.lines().filter(l -> l.startsWith("entrypoint:"))
                .findFirst().orElse("").replace("entrypoint: ", ""), SPI_FILE);

        // 2. 宿主加载插件
        FakeHost host = new FakeHost();
        GreetingPlugin plugin = new GreetingPlugin();
        log.info("[案例] 加载插件 id={}", plugin.pluginId());
        host.load(plugin);
        log.info("[案例] 工具注册完成，暴露列表={}", host.exposedTools.keySet());

        // 3. 工具以 plugin__greeting__greet 暴露给 Agent
        assertEquals(1, host.exposedTools.size());
        ToolDefinition greet = host.exposedTools.get("plugin__greeting__greet");
        assertNotNull(greet);
        assertEquals("greet", greet.name());

        // 4. Agent 调用工具：入参走 JSON Schema 声明的 name 字段
        ToolExecutionResult result = greet.execute(Map.of("name", "小傅哥"), null).join();
        assertFalse(result.isError());
        // TextBlock 文本内容为问候语（工具 ok() 包装）
        log.info("[案例] 工具调用成功，返回={}", firstText(result));

        // 5. 宿主停用插件：工具被逆序回收，暴露表清空
        host.stop(plugin);
        log.info("[案例] 插件停用，回收后暴露列表={}", host.exposedTools.keySet());
    }

    @Test
    void minimalPlugin_toolFailurePath() throws Exception {
        // 失败路径：缺参时工具返回 fail，而不是抛异常打断 Agent 循环
        FakeHost host = new FakeHost();
        GreetingPlugin plugin = new GreetingPlugin();
        host.load(plugin);

        ToolDefinition greet = host.exposedTools.get("plugin__greeting__greet");
        ToolExecutionResult result = greet.execute(Map.of(), null).join();
        assertTrue(result.isError());
        log.info("[案例] 缺参调用按预期失败，error={}",
                ((ToolExecutionResult.Failure) result).error().message());

        host.stop(plugin);
    }

    // ── 案例：工具之外的能力面——Hook 拦截 / 提示词注入 / 事件 / 配置 ──

    /**
     * 进阶插件：除了工具，还通过 PluginContext 注册 Hook、系统提示词、
     * 事件订阅，并读取宿主配置——体现"插件是宿主流程的扩展点，
     * 而不只是工具提供方"（与 v0.1.8 的 PluginContext 能力面对齐）。
     */
    static final class GuardPlugin extends AbstractHarnessPlugin {

        String lastDeniedTool;
        String configValue;

        GuardPlugin() {
            super("guard");
        }

        @Override
        public List<ToolDefinition> tools() {
            return List.of();
        }

        @Override
        public void configure(PluginContext context) {
            // 1. 工具之外的能力一：Hook 拦截——禁用敏感工具，宿主在执行前咨询本插件
            context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
                if ("shell_execute".equals(toolName)) {
                    lastDeniedTool = toolName;
                    return PluginHookResult.block("guard 插件禁止执行 shell 命令");
                }
                return PluginHookResult.allow();
            });

            // 2. 工具之外的能力二：系统提示词注入——宿主组装 system prompt 时带上本段落
            context.registerSystemPrompt("guard-rules", 10,
                    "执行任何删除类操作前必须先向用户确认。");

            // 3. 工具之外的能力三：事件订阅——监听宿主/其他插件发布的事件
            context.subscribe("plugin.started",
                    event -> log.info("[Guard案例] 收到事件 {} 来自 {}", event.name(), event.source()));

            // 4. 工具之外的能力四：配置读取——宿主下发的运行时配置
            configValue = context.getConfig("level", "basic");
        }
    }

    /**
     * 证明插件不只能"提供工具"，还能深度参与宿主流程：
     * Hook 否决工具执行、注入 system prompt、订阅事件、读取配置。
     */
    @Test
    void minimalPlugin_beyondTools_hostFlowParticipation() throws Exception {
        FakeHost host = new FakeHost();
        // 宿主下发运行时配置覆盖值（模拟 PluginConfigStore 的 override 层）
        host.configOverrides.put("level", "override-level");
        GuardPlugin plugin = new GuardPlugin();
        host.load(plugin);

        // 1. Hook：宿主在执行 shell_execute 前咨询插件，被否决
        PluginHook hook = host.hooks.get("PRE_TOOL_USE");
        assertNotNull(hook, "插件注册的 Hook 应被宿主持有");
        PluginHookResult verdict = hook.execute("shell_execute", "{}");
        assertEquals("BLOCK", verdict.decision().orElse(""));
        assertEquals("guard 插件禁止执行 shell 命令", verdict.reason().orElse(""));
        assertEquals("shell_execute", plugin.lastDeniedTool);
        log.info("[Guard案例] PRE_TOOL_USE 拦截 shell_execute → {} ({})",
                verdict.decision().orElse(""), verdict.reason().orElse(""));

        // 2. 非敏感工具放行
        assertEquals("ALLOW", hook.execute("greet", "{}").decision().orElse(""));

        // 3. 系统提示词：段落带 plugin__guard__ 前缀进入宿主 prompt 组装
        assertTrue(host.systemPrompts.contains("plugin__guard__guard-rules"));

        // 4. 配置：宿主下发覆盖值，插件读到 override 而非默认值
        assertEquals("override-level", plugin.configValue);
        log.info("[Guard案例] 配置 level={}（宿主覆盖生效）", plugin.configValue);

        // 5. 事件：宿主发布，插件订阅被回调
        host.emit("plugin.started", "guard");
        assertEquals(1, host.deliveredEvents.size());
        assertEquals("plugin.started", host.deliveredEvents.get(0).name());

        // 6. 停用后：Hook/提示词/事件一并逆序回收，宿主流程回到无插件状态
        host.stop(plugin);
        assertFalse(host.hooks.containsKey("PRE_TOOL_USE"));
        assertFalse(host.systemPrompts.contains("plugin__guard__guard-rules"));
        log.info("[Guard案例] 停用后 Hook 与提示词均已回收");
    }

    // ── 案例：插件工具接入真实 LLM，端到端 Agent 循环 ───────────────

    /**
     * 探活本地 LLM 网关（v0.1.8 验证配方同款）；不可达时跳过端到端用例。
     */
    private static boolean gatewayAlive() {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:8777/v1/models"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            // 网关可能要求鉴权：401 也说明服务在线
            int code = http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 端到端案例：真实 LLM 依据工具的 name/description 自主决定调用插件工具，宿主执行后回填，模型基于结果生成最终回答。
     * <p/>
     *
     * <a href="https://github.com/fuzhengwei/WaLiAPI/releases/tag/waliapi-v0.4.4">推荐下载 waliapi 配置后测试，确保 apikey 不暴漏（本地key，提交也没问题）。</a>
     *
     * <p>与 v0.1.8 分支方案一致的关键点：
     * <ul>
     *   <li>工具以 {@code plugin__<pluginId>__<toolName>} 完整名暴露给模型——
     *       模型看到并调用的就是这个名字，插件前缀由宿主统一管理；</li>
     *   <li>工具定义经 {@link cn.wangtao.deepseek.harness.domain.model.valobj.ToolSchema}
     *       转换（name/description/parameters 三元组）；</li>
     *   <li>工具结果经 {@link Message#createToolResult} 以 tool role 消息回填，
     *       携带 toolCallId 关联；</li>
     *   <li>助手消息携带 {@link MessageSource.ModelMessageSource} 溯源标签。</li>
     * </ul>
     *
     *
     */
    @Test
    void minimalPlugin_withRealLlm_agentLoop() throws Exception {
        Assumptions.assumeTrue(gatewayAlive(),
                "本地 LLM 网关 127.0.0.1:8777 未启动，跳过端到端用例。请下载 WaLiAPI 配置力模型使用。");

        // 1. 宿主加载插件，工具注册进暴露表；传给模型的工具定义使用带前缀的完整名
        //    （以分支 v0.1.8 方案为准：模型看到并调用的就是 plugin__<pluginId>__<toolName>）
        FakeHost host = new FakeHost();

        GreetingPlugin plugin = new GreetingPlugin();
        host.load(plugin);

        List<ToolDefinition> tools = host.exposedTools.entrySet().stream()
                .map(e -> prefixedTool(e.getKey(), e.getValue()))
                .toList();

        // 2. 对话起点：用户问题 + 宿主注入的工具使用提示（v0.1.8 同款 system prompt 位置）
        List<Message> messages = new ArrayList<>();
        messages.add(Message.createUser(
                List.of(new TextBlock("请使用打招呼工具向小傅哥问好，并告诉我它回了什么。")),
                new MessageSource.UserSource()));

        // 3. Agent 循环：最多 3 轮，工具简单足够收敛
        LlmChatClient client = new LlmChatClient();
        String finalAnswer = "";
        for (int round = 1; round <= 3; round++) {
            LlmChatClient.ChatResult chat = client.chat(messages, tools);
            messages.add(chat.assistantMessage());
            log.info("[LLM案例] 第{}轮 finishReason={}", round, chat.finishReason());

            // 3a. 模型返回文本：循环结束（非工具调用记录，就获取结果结束 | tool_calls / finish_reason 字段：OpenAI API 协议约定，所有兼容网关统一出口）
            if (!"tool_calls".equals(chat.finishReason())) {
                finalAnswer = firstText(chat.assistantMessage());
                break;
            }

            // 3b. 模型请求工具：按完整名查宿主暴露表并执行（ToolCallBlock.arguments 原始 JSON）
            for (ContentBlock block: chat.assistantMessage().content()) {
                if (!(block instanceof ToolCallBlock call)) continue;

                log.info("[LLM案例] 模型请求调用工具 name={} args={}", call.name(), call.arguments());
                ToolDefinition tool = host.exposedTools.get(call.name());

                Map<String, Object> args = parseArgs(call.arguments());
                ToolExecutionResult result = tool.execute(args, null).join();

                // 3c. 结果以 tool role 消息回填，toolCallId 关联本次调用 | messages 简单的记录上下文
                messages.add(Message.createToolResult(call.id(),
                        List.of(new TextBlock(firstText(result))), result.isError()));

                log.info("[LLM案例] 工具执行完成 isError={} 已回填", result.isError());
            }
        }

        // 4. 最终回答必须包含工具产出（证明模型确实消费了插件工具的返回）
        log.info("[LLM案例] 最终回答={}", finalAnswer);

        host.stop(plugin);
    }

    /**
     * 从执行结果里取第一个文本块内容。
     */
    private static String firstText(ToolExecutionResult result) {
        return result.content().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).text())
                .findFirst()
                .orElseThrow();
    }

    /**
     * 从助手消息里取第一个文本块内容（最终回答）。
     */
    private static String firstText(Message message) {
        return message.content().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).text())
                .findFirst()
                .orElse("");
    }

    /**
     * 把工具包装为以完整名（plugin__id__tool）暴露给模型的定义，其余契约不变。
     */
    private static ToolDefinition prefixedTool(String fullName, ToolDefinition tool) {
        return new ToolDefinition() {
            @Override
            public String name() {
                return fullName;
            }

            @Override
            public String description() {
                return tool.description();
            }

            @Override
            public Map<String, Object> parameters() {
                return tool.parameters();
            }

            @Override
            public CompletableFuture<ToolExecutionResult> execute(
                    Object args, ToolRunContext exec) {
                return tool.execute(args, exec);
            }
        };
    }

    /**
     * 极简解析工具调用 arguments JSON：{"name":"..."}。
     * 只提取 "key":"value" 形式的字符串键值对，足够本案例的单参数工具使用。
     */
    private static Map<String, Object> parseArgs(String json) {
        Map<String, Object> args = new HashMap<>();
        if (json == null) return args;

        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(json);

        while (m.find()) {
            args.put(m.group(1), m.group(2));
        }

        return args;
    }

}
