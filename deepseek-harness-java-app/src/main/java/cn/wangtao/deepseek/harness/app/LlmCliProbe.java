package cn.wangtao.deepseek.harness.app;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import cn.wangtao.deepseek.harness.domain.llm.adapter.port.ILlmRuntimePort;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import cn.wangtao.deepseek.harness.domain.llm.service.LlmGenerationService;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.InMemoryLlmRuntimePort;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek.DeepSeekAdapter;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import cn.wangtao.deepseek.harness.infrastructure.adapter.llm.deepseek.DirectSeekCatalogModel;

/**
 * xfg-2-1 验收入口：CLI 一次非流式对话。
 *
 * <h2>设计目的</h2>
 * <p>
 * 这是整个 Harness 学习路线的第一个"真实可跑"的验证入口。它的目标不是做产品功能，
 * 而是用一条最小的命令行链路，验证 4 件事：
 * <ol>
 *   <li><b>端口（Port）</b>：{@link ILlmRuntimePort} 是领域层定义的"插座"，上层只认插座，不认具体厂商；</li>
 *   <li><b>适配器（Adapter）</b>：{@link DeepSeekAdapter} 是插到插座上的"电器"，把领域请求翻译成
 *       OpenAI 兼容的 HTTP 请求；</li>
 *   <li><b>领域服务</b>：{@link LlmGenerationService} 把流式 chunk 组装成一条完整的 Message；</li>
 *   <li><b>配置驱动</b>：换厂商只需要换 baseUrl/apiKey/model，代码一行不改（本类正是用
 *       WaLiAPI 网关 + GLM 模型验证的，说明适配器对"OpenAI 兼容协议"是通用的）。</li>
 * </ol>
 *
 * <h2>组装链路（与后续章节完全一致）</h2>
 * <pre>
 *   main()
 *     └─ InMemoryLlmRuntimePort          ← 端口：内存路由表 provider → adapter
 *          └─ DeepSeekAdapter            ← 适配器：领域 StreamChunk ⇄ HTTP/SSE
 *               └─ LlmGenerationService  ← 领域服务：流式 → 阻塞式 Message
 * </pre>
 *
 * <h2>手工运行方式</h2>
 * <pre>
 * # 方式一：环境变量指定 Key（推荐）
 * DEEPSEEK_API_KEY=sk-xxx mvn -pl deepseek-harness-java-app exec:java \
 *   -Dexec.mainClass=cn.xiaofuge.deepseek.harness.app.LlmCliProbe
 *
 * # 方式二：直接跑（未设环境变量时回落到类内 WaLiAPI 网关默认配置）
 * mvn -pl deepseek-harness-java-app exec:java \
 *   -Dexec.mainClass=cn.xiaofuge.deepseek.harness.app.LlmCliProbe \
 *   -Dexec.args='你好，用一句话介绍 Java 的 Flow API'
 * </pre>
 *
 * <p>没有配置任何 Key 时打印装配信息并跳过真实调用，保证构建与启动不被阻塞。</p>
 *
 * @author wt
 */
public class LlmCliProbe {

    /**
     * 默认 API Key：指向本地 WaLiAPI 网关（OpenAI 兼容协议），方便一键体验。
     * <p>运行时优先读环境变量 {@code DEEPSEEK_API_KEY}，未设置才使用此默认值，
     * 这样既不影响真实 DeepSeek Key 的使用，又免去了新人配置的门槛。</p>
     */
    private static final String API_KEY = "sk-waliapi-4f8895c43a6c4bc1ae6173fd0915c0e9";

    /**
     * 默认网关地址：WaLiAPI 本地网关，暴露 OpenAI 兼容的 {@code /v1/chat/completions}。
     * <p>适配器内部会用 {@code baseUrl + /v1/chat/completions} 拼出最终请求地址。</p>
     */
    private static final String BASE_URL = "http://127.0.0.1:8777";

    /**
     * 默认模型：WaLiAPI 网关侧配置的 GLM 模型名。
     * <p>同一套代码换模型只需要改这一个字符串。</p>
     */
    private static final String MODEL = "glm-5.3-flash";

    /**
     * 程序入口：装配最小 LLM 链路并发起一次非流式对话。
     *
     * <p>运行距离（从启动到结束的完整路径）：</p>
     * <ol>
     *   <li>读取配置（环境变量优先，回落类内常量）；</li>
     *   <li>new 一个内存端口 {@link InMemoryLlmRuntimePort}（此时路由表为空）；</li>
     *   <li>构造 {@link DeepSeekAdapterOptions}（baseUrl/key/model 目录），用它 new 出
     *       {@link DeepSeekAdapter}，注册到端口（路由表变为 deepseek → adapter）；</li>
     *   <li>把用户问题包装成 {@link GenerateOptions}（领域层统一的"生成请求"值对象）；</li>
     *   <li>调用 {@link LlmGenerationService#generate}：内部走
     *       端口.stream() → 适配器 HTTP/SSE → chunk 逐个回流 → {@link StreamChunk} 累积成 Message；</li>
     *   <li>从 Message 的内容块中拼接纯文本并打印，结束。</li>
     * </ol>
     *
     * @param args 可选：把命令行参数拼成提问内容；不传时使用默认问题
     */
    public static void main(String[] args) {
        // 三个配置都可以被环境变量覆盖，方便在不改代码的情况下切换真实厂商
        String apiKey = System.getenv().getOrDefault("DEEPSEEK_API_KEY", API_KEY);
        String baseUrl = System.getenv().getOrDefault("DEEPSEEK_BASE_URL", BASE_URL);
        String model = System.getenv().getOrDefault("DEEPSEEK_MODEL", MODEL);

        System.out.println("=== xfg-2-1 LLM CLI Probe ===");
        System.out.println("baseUrl = " + baseUrl);
        System.out.println("model   = " + model);

        // 1. 端口 + 适配器装配（后续章节会改为 Spring 装配）
        //    InMemoryLlmRuntimePort 本质是一个 ConcurrentHashMap<String, LlmAdapter>，
        //    "注册"就是把 deepseek → adapter 这条映射写进去。
        InMemoryLlmRuntimePort runtimePort = new InMemoryLlmRuntimePort();
        DeepSeekAdapterOptions options = new DeepSeekAdapterOptions(
                baseUrl,
                apiKey == null ? "" : apiKey,
                DeepSeekAdapter.DEFAULT_MAX_TOKENS,
                DeepSeekAdapter.DEFAULT_CONTEXT_WINDOW,
                DeepSeekAdapter.DEFAULT_STREAM_IDLE_TIMEOUT_MS,
                // 模型目录：最小版不做上游发现，直接把要用的模型写进配置
                List.of(new DirectSeekCatalogModel(model, model, null,
                        Integer.valueOf(DeepSeekAdapter.DEFAULT_CONTEXT_WINDOW),
                        Integer.valueOf(DeepSeekAdapter.DEFAULT_MAX_TOKENS))),
                null
        );

        // 注册模型对个的实现类
        runtimePort.registerAdapter("deepseek", new DeepSeekAdapter(options, new ObjectMapper()));
        System.out.println("registered providers = " + runtimePort.listProviders());

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("未检测到 DEEPSEEK_API_KEY，跳过真实调用（装配验证通过）。");
            return;
        }

        // 2. 一次阻塞式非流式对话
        //    命令行传了参数就当问题用，否则用默认问题
        String question = args.length > 0 ? String.join(" ", args)
                : "用一句话介绍 Java 的 Flow API。";
        System.out.println("question: " + question);

        // GenerateOptions 是领域层统一的"生成请求"：
        // provider 决定路由到哪个适配器，model 决定上游用哪个模型，
        // messages 是对话内容，system 是系统提示词，其余参数本节暂不使用（传 null）。
        GenerateOptions genOptions = new GenerateOptions(
                "deepseek", model, null,
                List.of(Message.createUser(List.of(new TextBlock(question)), new MessageSource.UserSource())),
                "你是一个简洁的助手。", null, null, null, null, null, null, null, null, null
        );

        // generate() 返回 CompletableFuture，join() 阻塞等待流式 chunk 全部组装完
        LlmGenerationService service = new LlmGenerationService(runtimePort);
        Message answer = service.generate(genOptions).join();

        // 3. 从 Message 的内容块列表里抽出文本块并拼接（模型回答可能包含文本/推理/工具调用等多种块）
        StringBuilder text = new StringBuilder();
        for (var block : answer.content()) {
            if (block instanceof TextBlock tb) {
                text.append(tb.text());
            }
        }

        System.out.println("answer : " + text);
        System.out.println("=== done ===");
    }
}
