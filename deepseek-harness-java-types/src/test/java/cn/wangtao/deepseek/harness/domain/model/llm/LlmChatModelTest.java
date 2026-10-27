package cn.wangtao.deepseek.harness.domain.model.llm;

import cn.wangtao.deepseek.harness.domain.model.entity.AbstractTool;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecution;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecutionInput;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.wangtao.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.wangtao.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ImageAttachmentRef;
import cn.wangtao.deepseek.harness.domain.model.valobj.ImageBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ReasoningBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolCallBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolExecutionMode;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolFailure;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolResultBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 对接集成案例：通过真实 API 调用（127.0.0.1:8777，模型 glm-5.3-flash）
 * 验证 types 模块下各领域对象/值对象的实际用途。
 *
 * <p>六个用例按对话能力层层递进，每个用例聚焦一组对象：</p>
 * <ol>
 *   <li>单轮对话 —— Message / TextBlock / MessageSource.UserSource / ModelMessageSource</li>
 *   <li>多轮对话 —— Message 作为持久化历史 + ModelMessageSource 还原助手历史</li>
 *   <li>思维链 —— ReasoningBlock 与 TextBlock 的分离</li>
 *   <li>工具调用全链路 —— ToolDefinition / AbstractTool / ToolSchema / ToolCallBlock /
 *       ToolExecutionInput / ToolExecution / ToolRunContext / ToolExecutionResult /
 *       ToolResultBlock / ToolMessageSource / ToolExecutionMode</li>
 *   <li>工具失败回传 —— ToolFailure / ToolExecutionResult.Failure 的错误反馈闭环</li>
 *   <li>图片对象语义（本地）—— ImageBlock / ImageAttachmentRef / PluginSource</li>
 * </ol>
 *
 * <p>运行方式（工程默认 skipTests=true，需显式打开）：<br>
 * {@code mvn test -pl deepseek-harness-java-types -Dtest=LlmChatModelTest -DskipTests=false}</p>
 *
 */
class LlmChatModelTest {

    /** 被测客户端：所有用例共用一个轻量实例。 */
    private final LlmChatClient client = new LlmChatClient();

    // ── 演示用工具定义 ──────────────────────────────────────────────────

    /**
     * 天气查询工具：继承 {@link AbstractTool}，只实现 name/description/parameters/run 四个方法，
     * 即获得"Schema 声明 + 类型安全执行入口"的完整工具能力。
     */
    static final class WeatherTool extends AbstractTool {
        @Override public String name() { return "get_weather"; }
        @Override public String description() { return "查询指定城市的实时天气"; }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("city", stringSchema("城市名称，如：北京"))
                    .required("city")
                    .build();
        }
        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok("晴，气温 25 摄氏度，湿度 40%。");
        }
    }

    /**
     * 永远失败的时间查询工具：用于演示 {@link ToolFailure} 失败结果如何回传模型。
     */
    static final class BrokenClockTool extends AbstractTool {
        @Override public String name() { return "get_time"; }
        @Override public String description() { return "查询当前时间"; }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("timezone", stringSchema("时区，如 Asia/Shanghai，可省略"))
                    .build();
        }
        @Override
        protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return fail("时间服务暂时不可用，请稍后再试或基于已有信息回答。", "TIME_SERVICE_DOWN");
        }
    }

    // ── 用例 1：单轮对话 ────────────────────────────────────────────────

    /**
     * 用例 1【单轮对话】—— 验证最基础的消息投递链路。
     *
     * <p>验证对象与用途：</p>
     * <ul>
     *   <li>{@link Message#createUser} —— 用户消息工厂：创建即分配稳定 ID，role=user；</li>
     *   <li>{@link TextBlock} —— 用户输入正文的内容块；</li>
     *   <li>{@link MessageSource.UserSource} —— 标记消息由人类用户产生；</li>
     *   <li>{@link MessageSource.ModelMessageSource} —— 模型回复自动打上 provider/model 溯源标签，
     *       由 LlmChatClient 在响应映射时统一附加。</li>
     * </ul>
     *
     * <p>流程：构造用户消息 → 真实 API 调用 → 断言回复是 assistant 角色、
     * 来源为 ModelMessageSource（provider=kimi, model=kimi-k2.6）、正文非空。</p>
     */
    @Test
    void singleTurn_userMessage_getsModelReplyWithSourceTag() {
        // 1. 领域侧构造用户消息：正文进 TextBlock，来源标记为 UserSource
        Message userMessage = Message.createUser(
                List.of(new TextBlock("用一句话介绍你自己，不超过 30 个字。")),
                new MessageSource.UserSource());
        assertEquals("user", userMessage.role());

        // 2. 真实调用本地网关
        LlmChatClient.ChatResult result = client.chat(List.of(userMessage));

        // 3. 模型回复：role=assistant，来源带 provider/model 溯源
        Message reply = result.assistantMessage();
        assertEquals("assistant", reply.role());
        MessageSource.ModelMessageSource source =
                assertInstanceOf(MessageSource.ModelMessageSource.class, reply.source());
        assertEquals("kimi", source.provider());
        assertEquals("kimi-k2.6", source.model());

        // 4. 可见正文在 TextBlock 中，非空
        String text = firstText(reply);
        assertTrue(text != null && !text.isBlank(), "模型应回复非空文本，实际: " + text);
        System.out.println("[用例1] 模型回复: " + text);
    }

    // ── 用例 2：多轮对话 ────────────────────────────────────────────────

    /**
     * 用例 2【多轮对话】—— 验证 {@link Message} 同时充当"持久化历史"的用途。
     *
     * <p>验证对象与用途：</p>
     * <ul>
     *   <li>{@link Message} —— 除投递外，消息列表整体即会话历史，原样再次发送即可延续上下文；</li>
     *   <li>{@link Message#createAssistant} + {@link MessageSource.ModelMessageSource} ——
     *       历史中的助手消息按领域模型重建（模拟从存储中恢复），重建后可无缝进入下一轮请求。</li>
     * </ul>
     *
     * <p>流程：第一轮告知事实 → 历史重建（user + assistant + 新 user）→ 第二轮提问。
     * 若历史被正确携带，模型应能答出第一轮告知的宠物名字与品种。</p>
     */
    @Test
    void multiTurn_historyMessages_carryContextAcrossRounds() {
        // 1. 第一轮：告知事实
        Message turn1User = Message.createUser(
                List.of(new TextBlock("我的宠物叫旺财，它是一只柯基犬。请记住。")),
                new MessageSource.UserSource());
        LlmChatClient.ChatResult turn1 = client.chat(List.of(turn1User));
        System.out.println("[用例2] 第一轮回复: " + firstText(turn1.assistantMessage()));

        // 2. 模拟"历史从存储恢复"：用领域工厂重建第一轮的助手消息（来源标签随历史一并还原）
        Message turn1Assistant = Message.createAssistant(
                List.of(new TextBlock("好的，我记住了：你的宠物叫旺财，是一只柯基犬。")),
                new MessageSource.ModelMessageSource("kimi", "kimi-k2.6"));

        // 3. 第二轮：完整历史（user + assistant + 新 user）一起发送
        Message turn2User = Message.createUser(
                List.of(new TextBlock("我的宠物叫什么名字？是什么品种？")),
                new MessageSource.UserSource());
        LlmChatClient.ChatResult turn2 = client.chat(
                List.of(turn1User, turn1Assistant, turn2User));

        // 4. 历史生效：模型能答出第一轮的事实
        String answer = firstText(turn2.assistantMessage());
        assertTrue(answer.contains("旺财"), "应包含宠物名字'旺财'，实际: " + answer);
        assertTrue(answer.contains("柯基"), "应包含品种'柯基'，实际: " + answer);
        System.out.println("[用例2] 第二轮回复: " + answer);
    }

    // ── 用例 3：思维链 ──────────────────────────────────────────────────

    /**
     * 用例 3【思维链】—— 验证 {@link ReasoningBlock} 的用途：与可见文本分离。
     *
     * <p>验证对象与用途：</p>
     * <ul>
     *   <li>{@link ReasoningBlock} —— 承载模型返回的 reasoning_content（思维链）。
     *       它与"说给用户听的话"（TextBlock）语义不同：前端可独立折叠展示、
     *       持久化时可选择不落库。本用例证明两者在同一条消息里是独立的内容块。</li>
     * </ul>
     *
     * <p>流程：提出需要思考的小问题 → 真实调用 → 若模型返回思维链，
     * 断言消息内容中 ReasoningBlock 与 TextBlock 并存且互不掺杂。</p>
     */
    @Test
    void reasoningBlock_keptSeparateFromVisibleText() {
        Message userMessage = Message.createUser(
                List.of(new TextBlock("一个笼子里有鸡和兔共 8 个头、26 条腿，各有几只？只给答案。")),
                new MessageSource.UserSource());
        LlmChatClient.ChatResult result = client.chat(List.of(userMessage));

        Message reply = result.assistantMessage();
        String visibleText = firstText(reply);
        assertTrue(visibleText != null && !visibleText.isBlank(), "可见答案非空");

        // 收集思维链块与文本块，验证"分离"语义
        List<ReasoningBlock> reasoning = new ArrayList<>();
        List<TextBlock> texts = new ArrayList<>();
        for (ContentBlock b : reply.content()) {
            if (b instanceof ReasoningBlock r) reasoning.add(r);
            if (b instanceof TextBlock t) texts.add(t);
        }

        // glm-5.3-flash 正常会返回思维链；若某次调用未返回则跳过结构断言（不误报）
        if (reasoning.isEmpty()) {
            System.out.println("[用例3] 本次调用未返回思维链，跳过 ReasoningBlock 断言。可见答案: " + visibleText);
            return;
        }
        // 思维链内容独立于可见文本：思维链在做算术，可见文本是最终答案
        System.out.println("[用例3] 思维链(前80字): "
                + reasoning.get(0).text().substring(0, Math.min(80, reasoning.get(0).text().length())));
        System.out.println("[用例3] 可见答案: " + visibleText);
        assertNotEquals(reasoning.get(0).text(), texts.get(texts.size() - 1).text(),
                "思维链与可见文本应是两个独立内容块");
        assertTrue(visibleText.contains("3") && visibleText.contains("5"),
                "鸡兔同笼答案应为鸡3兔5，实际: " + visibleText);
    }

    // ── 用例 4：工具调用全链路 ──────────────────────────────────────────

    /**
     * 用例 4【工具调用全链路】—— 最核心用例，一次跑通 Agent Loop 的"请求工具 → 本地执行 → 结果回传"闭环。
     *
     * <p>验证对象与用途（按出场顺序）：</p>
     * <ul>
     *   <li>{@link ToolDefinition} / {@link AbstractTool} —— 工具契约与模板基类：
     *       WeatherTool 只实现 4 个方法即完成注册所需的全部声明；</li>
     *   <li>{@link ToolSchema} —— 发送给模型的工具三要素（name/description/parameters JSON Schema），
     *       LlmChatClient 序列化 wire 报文时经由它中转；</li>
     *   <li>{@link ToolCallBlock} —— 模型返回的工具调用（id/name/arguments），
     *       arguments 刻意保持原始 JSON 字符串，谁消费谁解析；</li>
     *   <li>{@link ToolExecutionInput} → {@link ToolExecution} ——
     *       调用方输入 → 注册表分配执行身份（含 rootCallId 与 token）；</li>
     *   <li>{@link ToolExecutionMode} —— 调度模式：默认 isConcurrencySafe=false → EXCLUSIVE 串行屏障；</li>
     *   <li>{@link ToolRunContext} —— 工具运行时上下文：注册表身份只读可查（callId 对账）；</li>
     *   <li>{@link ToolExecutionResult.Success} —— 标准化执行结果；</li>
     *   <li>{@link ToolResultBlock} + {@link Message#createToolResult} +
     *       {@link MessageSource.ToolMessageSource} —— 结果包装为 role=tool 的消息，靠 callId 与调用配对。</li>
     * </ul>
     */
    @Test
    void toolCall_fullLoop_fromModelRequestToResultFeedback() throws Exception {
        WeatherTool weatherTool = new WeatherTool();

        // ── 第一阶段：模型决定调用工具 ──
        Message userMessage = Message.createUser(
                List.of(new TextBlock("北京今天天气怎么样？必须先调用工具查询，再用查询结果回答。")),
                new MessageSource.UserSource());
        LlmChatClient.ChatResult first = client.chat(List.of(userMessage), List.of(weatherTool));

        // finish_reason=tool_calls：模型没有直接回答，而是请求执行工具
        assertEquals("tool_calls", first.finishReason(), "模型应请求工具调用");

        // ToolCallBlock：从助手消息内容块中取出模型生成的调用描述
        ToolCallBlock call = first.assistantMessage().content().stream()
                .filter(b -> b instanceof ToolCallBlock)
                .map(b -> (ToolCallBlock) b)
                .findFirst().orElseThrow(() -> new AssertionError("应有 ToolCallBlock"));
        assertEquals("get_weather", call.name(), "应调用天气工具");
        System.out.println("[用例4] 模型请求工具: " + call.name() + " args=" + call.arguments());

        // ToolSchema：发送给模型的工具三要素（此处验证其与工具定义一致）
        ToolSchema schema = new ToolSchema(weatherTool.name(), weatherTool.description(), weatherTool.parameters());
        assertEquals("get_weather", schema.name());
        assertTrue(schema.parameters().containsKey("properties"), "参数 Schema 应含 properties");

        // ── 第二阶段：本地执行工具 ──
        // ToolCallBlock → ToolExecutionInput（此刻才开始解析 arguments）
        Object parsedArgs = MiniJson.parse(call.arguments());
        ToolExecutionInput input = ToolExecutionInput.root(call.id(), call.name(), parsedArgs);

        // ToolExecutionInput → ToolExecution：注册表分配执行身份（rootCallId + token）
        ToolExecution execution = ToolExecution.from(input);
        assertEquals(call.id(), execution.callId());
        assertEquals(execution.callId(), execution.rootCallId(), "根调用的 rootCallId 等于 callId");

        // ToolExecutionMode：调度模式由 isConcurrencySafe 决定，默认串行（EXCLUSIVE）
        ToolExecutionMode mode = weatherTool.isConcurrencySafe(parsedArgs)
                ? ToolExecutionMode.PARALLEL : ToolExecutionMode.EXCLUSIVE;
        assertEquals(ToolExecutionMode.EXCLUSIVE, mode);

        // ToolRunContext：工具影响循环的唯一受控通道；execution() 提供只读身份供 callId 对账
        ToolRunContext ctx = new ToolRunContext(execution);
        assertEquals(call.id(), ctx.execution().callId(), "上下文身份应与 ToolCallBlock.id 对账一致");

        // AbstractTool.execute：类型安全执行入口（args 已是 Map）
        ToolExecutionResult result = weatherTool.execute(parsedArgs, ctx).get();
        assertFalse(result.isError(), "天气工具应执行成功");
        assertInstanceOf(ToolExecutionResult.Success.class, result);
        System.out.println("[用例4] 工具执行结果: " + firstTextOf(result.content()));

        // ── 第三阶段：结果回传，模型产出最终回答 ──
        // ToolExecutionResult.content → Message.createToolResult：
        // 包装为 role=tool 的消息，ToolMessageSource 携带 callId 完成配对
        Message toolResultMessage = Message.createToolResult(call.id(), result.content(), result.isError());
        assertEquals("tool", toolResultMessage.role());
        MessageSource.ToolMessageSource toolSource =
                assertInstanceOf(MessageSource.ToolMessageSource.class, toolResultMessage.source());
        assertEquals(call.id(), toolSource.callId(), "工具结果靠 callId 与调用配对");

        // 完整历史：用户提问 + 模型的工具调用请求 + 工具结果 → 第二次调用
        LlmChatClient.ChatResult second = client.chat(
                List.of(userMessage, first.assistantMessage(), toolResultMessage));

        // 模型基于工具结果作答：回复应包含工具给出的天气事实
        String finalAnswer = firstText(second.assistantMessage());
        assertTrue(finalAnswer.contains("25"), "最终回答应基于工具结果(气温25)，实际: " + finalAnswer);
        System.out.println("[用例4] 最终回答: " + finalAnswer);
    }

    // ── 用例 5：工具失败回传 ────────────────────────────────────────────

    /**
     * 用例 5【工具失败回传】—— 验证 {@link ToolFailure} 与 {@link ToolExecutionResult.Failure}
     * 的用途：失败结果也会回填给模型，让模型看到错误后自行调整话术。
     *
     * <p>验证对象与用途：</p>
     * <ul>
     *   <li>{@link ToolFailure} —— 结构化错误信息（message + ToolErrorInfo{code}），
     *       供上层按错误码分类处理；</li>
     *   <li>{@link ToolExecutionResult.Failure} —— isError()=true 的标准化失败结果，
     *       content 里携带给模型看的错误说明；</li>
     *   <li>{@link Message#createToolResult}(isError=true) —— 失败同样走"工具结果消息"通道回传，
     *       形成"调用失败 → 模型致歉/降级"的闭环。</li>
     * </ul>
     */
    @Test
    void toolFailure_errorResultFeedsBackToModel() throws Exception {
        BrokenClockTool brokenClock = new BrokenClockTool();

        // 1. 用户提问 → 模型请求调用（注定失败的）时间工具
        Message userMessage = Message.createUser(
                List.of(new TextBlock("现在几点了？必须先调用工具查询时间。")),
                new MessageSource.UserSource());
        LlmChatClient.ChatResult first = client.chat(List.of(userMessage), List.of(brokenClock));
        assertEquals("tool_calls", first.finishReason());

        ToolCallBlock call = first.assistantMessage().content().stream()
                .filter(b -> b instanceof ToolCallBlock)
                .map(b -> (ToolCallBlock) b)
                .findFirst().orElseThrow();
        assertEquals("get_time", call.name());

        // 2. 本地执行：拿到标准化的 Failure 结果
        Object parsedArgs = MiniJson.parse(call.arguments());
        ToolExecution execution = ToolExecution.from(
                ToolExecutionInput.root(call.id(), call.name(), parsedArgs));
        ToolExecutionResult result = brokenClock.execute(parsedArgs, new ToolRunContext(execution)).get();

        // 3. Failure 语义：isError=true + 结构化 ToolFailure（含错误码）
        assertTrue(result.isError());
        ToolExecutionResult.Failure failure = assertInstanceOf(ToolExecutionResult.Failure.class, result);
        assertEquals("TIME_SERVICE_DOWN", failure.error().info().code(), "错误码应保留在 ToolFailure.info");
        System.out.println("[用例5] 工具失败: " + failure.error().message());

        // 4. 失败结果同样回传模型（isError=true 的工具结果消息）
        Message toolResultMessage = Message.createToolResult(call.id(), result.content(), result.isError());
        ToolResultBlock block = assertInstanceOf(ToolResultBlock.class, toolResultMessage.content().get(0));
        assertTrue(block.isError(), "失败结果的 ToolResultBlock.isError 应为 true");

        // 5. 模型看到错误后自行调整：给出致歉/降级回答，而不是崩溃
        LlmChatClient.ChatResult second = client.chat(
                List.of(userMessage, first.assistantMessage(), toolResultMessage));
        String finalAnswer = firstText(second.assistantMessage());
        assertTrue(finalAnswer != null && !finalAnswer.isBlank(), "模型应基于失败信息给出回应");
        System.out.println("[用例5] 模型对失败的回应: " + finalAnswer);
    }

    // ── 用例 6：图片对象语义（本地验证）─────────────────────────────────

    /**
     * 用例 6【图片对象语义】—— 本地验证多模态输入相关对象的用途，不发真实请求。
     *
     * <p>验证对象与用途：</p>
     * <ul>
     *   <li>{@link ImageAttachmentRef} —— 附件元数据不可变对象：字节在紧凑构造器里防御性
     *       clone（外部数组改动不影响内部状态），与 List.copyOf 是同一套不可变纪律；</li>
     *   <li>{@link ImageBlock} —— 用户消息中的图片内容块：只持有附件引用，
     *       字节不散落在消息模型里，便于附件服务统一存储与去重；</li>
     *   <li>{@link MessageSource.PluginSource} —— 第三种来源：由插件产生的注入消息，
     *       与 UserSource/ModelMessageSource 并列，此处一并验证其可构造、可辨识。</li>
     * </ul>
     *
     * <p>说明：glm-5.3-flash 是否支持图片输入取决于网关配置，因此本用例不做多模态 API 调用，
     * 仅验证对象语义——图片块与文本块可并存于同一条用户消息，等待多模态网关接入后即可直接发送。</p>
     */
    @Test
    void imageObjects_attachmentRefDefensiveCopy_andPluginSource() {
        // 1x1 透明 PNG 的 Base64
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

        // ImageAttachmentRef：防御性 clone —— 修改外部数组不影响内部持有
        ImageAttachmentRef ref = new ImageAttachmentRef(png, "image/png", 1, 1);
        //创建 ref 之后，外部修改 png，会不会影响 ref 内部的数据。
        byte mutated = (byte) 0xFF;
        png[0] = mutated;
        assertNotEquals(mutated, ref.data()[0], "外部数组被改后，附件内部字节应保持不变");

        // ImageBlock + TextBlock：图片与文字并存于同一条用户消息（多模态输入）
        Message multimodalUser = Message.createUser(
                List.of(new ImageBlock(ref), new TextBlock("这张图里是什么？")),
                new MessageSource.UserSource());
        assertEquals(2, multimodalUser.content().size());
        assertInstanceOf(ImageBlock.class, multimodalUser.content().get(0));
        assertEquals("image/png", ((ImageBlock) multimodalUser.content().get(0)).attachment().mimeType());

        // PluginSource：插件产生的注入消息（如记忆插件补充上下文），可构造、可辨识
        Message pluginInjected = new Message(
                UUID.randomUUID().toString(),
                "user",
                List.of(new TextBlock("[记忆] 用户偏好简洁回答。")),
                new MessageSource.PluginSource("memory-plugin"));
        assertEquals("memory-plugin",
                ((MessageSource.PluginSource) pluginInjected.source()).plugin());
        System.out.println("[用例6] 图片块+文本块多模态消息与插件注入消息均构造成功");
    }

    // ── 小工具 ─────────────────────────────────────────────────────────

    /** 取消息中第一个 TextBlock 的文本（可能为 null）。 */
    private static String firstText(Message message) {
        for (ContentBlock b : message.content()) {
            if (b instanceof TextBlock t) return t.text();
        }
        return null;
    }

    /** 取内容块列表中第一个 TextBlock 的文本。 */
    private static String firstTextOf(List<ContentBlock> blocks) {
        for (ContentBlock b : blocks) {
            if (b instanceof TextBlock t) return t.text();
        }
        return null;
    }
}
