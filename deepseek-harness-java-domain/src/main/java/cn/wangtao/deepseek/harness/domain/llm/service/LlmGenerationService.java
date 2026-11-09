package cn.wangtao.deepseek.harness.domain.llm.service;

import cn.wangtao.deepseek.harness.domain.llm.adapter.port.ILlmRuntimePort;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.GenerateOptions;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM 生成领域服务：把流式响应组装成一条完整的模型消息。
 *
 * <h2>设计目的</h2>
 * <p>
 * 端口层只提供流式接口（{@code Flow.Publisher<StreamChunk>}），
 * 但很多调用方（CLI 探针、简单场景）只需要"问一句、答一句"的阻塞式语义。
 * 本服务是流与阻塞世界之间的<b>翻译官</b>：订阅流 → 把 chunk 喂给
 * {@link BlockAssembler} 累积 → 流结束后产出完整 {@link Message}。
 * Agent 编排等需要细粒度控制每条增量的场景，则绕过它直接订阅端口。
 *
 * <h2>运行距离（generate 被调用后发生什么）</h2>
 * <pre>
 *   generate(options)
 *     ├─ new BlockAssembler()                      ← chunk 累积器
 *     ├─ CountDownLatch(1)                         ← 流结束信号灯
 *     ├─ port.stream(options).subscribe(...)
 *     │     onNext  → assembler.push(chunk)        ← 每条增量实时累积
 *     │     onError → 记录异常 + countDown()
 *     │     onComplete → countDown()               ← 流结束
 *     └─ CompletableFuture.supplyAsync
 *           await()                                ← 阻塞等信号灯
 *           有异常 → 抛 RuntimeException
 *           无异常 → assembler.message(...)         ← 组装完整消息
 * </pre>
 *
 * <p>线程安全说明：onNext 在发布者线程回调，await 在 supplyAsync 线程等待，
 * 两者通过 CountDownLatch 建立 happens-before 关系；
 * error 用 AtomicReference 跨线程传递。</p>
 *
 * @author wt
 */
public class LlmGenerationService {

    /** 运行时端口：负责按 provider 路由到具体适配器。 */
    private final ILlmRuntimePort llmRuntimePort;

    /**
     * 注入运行时端口。
     *
     * @param llmRuntimePort LLM 运行时 Port（provider → 适配器路由表）
     */
    public LlmGenerationService(ILlmRuntimePort llmRuntimePort) {
        this.llmRuntimePort = llmRuntimePort;
    }

    /**
     * 发起一次生成，异步返回完整模型消息。
     *
     * <p>运行距离：见类注释的时序图。核心三步——订阅流、累积 chunk、
     * 流结束后组装 Message。返回的 CompletableFuture 让调用方自行决定
     * 阻塞（join）还是继续编排。</p>
     *
     * <p>数据案例：options = {provider:"deepseek", model:"glm-5.3-flash",
     * messages:[user:"你好"]} 时，流结束后返回
     * Message(role="assistant", content=[TextBlock("你好！...")])，
     * source 标记为 ModelMessageSource("deepseek", "glm-5.3-flash")；
     * 订阅阶段出错则 join 时抛 RuntimeException（原始异常为 cause）。</p>
     *
     * @param options 生成参数（provider/model/messages/system 等）
     * @return 完成后携带完整 assistant 消息；流失败则异常完成
     */
    // 调用大语言模型进行一次流式生成，但是不把每个 chunk 直接返回给调用方，而是等整个流结束后，
    // 将所有 chunk 组装成一条完整的 Message，再通过 CompletableFuture 异步返回。
    public CompletableFuture<Message> generate(GenerateOptions options) {
        // ── 1. 准备流式消费的三件套 ──────────────────────────
        // assembler：chunk 累积器，把 BlockStart/TextDelta/BlockEnd 等事件拼成完整消息
        var assembler = new BlockAssembler(); //BlockAssembler是消息组装器
        // done：流结束信号灯，onError/onComplete 任一到达即 countDown 放行等待线程
        var done = new CountDownLatch(1); //这是 Java 并发编程中的同步工具:让一个线程等待，直到另一个线程通知它任务完成。
        // error：跨线程异常传递（发布者线程写入，supplyAsync 线程读取）
        var error = new AtomicReference<Throwable>();// 跨线程传递异常

        // ── 2. 订阅端口流：每条 chunk 交给组装器，终态唤醒等待线程 ──
        llmRuntimePort.stream(options).subscribe(new Flow.Subscriber<>() {
            /**
             * 订阅建立回调：申请背压额度。
             *
             * <p>这里直接 request(Long.MAX_VALUE) 表示"不限速、来多少收多少"——
             * 本服务面向探针/简单问答场景，chunk 全部交给 assembler 内存累积，
             * 不存在消费不过来的问题。需要精细背压的场景（Agent 编排）应绕过本服务。</p>
             */
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            /**
             * 增量回调：每条领域事件（BlockStart/TextDelta/ReasoningDelta/
             * ToolCallDelta/BlockEnd/Usage/Finish）实时交给 {@link BlockAssembler} 累积。
             *
             * <p>数据案例：模型回答"你好"时，本方法会依次收到
             * BlockStart(0,"text") → TextDelta(0,"你") → TextDelta(0,"好")
             * → BlockEnd(0,TextBlock) → Finish(Stop)，全部 push 进 assembler。</p>
             */
            @Override
            public void onNext(StreamChunk item) {
                assembler.push(item);
            }

            /**
             * 流异常回调：记录异常并唤醒等待线程。
             *
             * <p>异常不在这里抛（回调线程抛了也没人接），只记入 AtomicReference，
             * 由下方 supplyAsync 线程检查后转为 CompletableFuture 异常完成。</p>
             *
             * <p>数据案例：适配器发出 Finish(Error) 后关闭流时， onError 收到的
             * 通常是取消/关闭信号；真正的业务失败信息在 assembler 收到的
             * Finish(Error) 事件里，两者由调用方按需关注。</p>
             *
             * @param throwable 流式过程中的异常
             */
            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }

            /**
             * 流正常结束回调：唤醒等待线程。
             *
             * <p>无论 Finish(Stop)/Finish(ToolCalls) 还是 Finish(Error)，
             * 适配器都以 onComplete 关闭流（错误走事件而非 onError），
             * 所以走到这里即认为流已正常消费完。</p>
             */
            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        // ── 3. 异步等待流结束并组装结果 ──────────────────────
        // supplyAsync 在独立线程池线程中阻塞 await()，调用方拿到
        // CompletableFuture 后可 join（阻塞）或继续编排（thenApply 等）
        return CompletableFuture.supplyAsync(() -> {
            // 阻塞等待信号灯：onError/onComplete 释放前这里不往下走
            try {
                done.await();
            } catch (InterruptedException exception) {
                // 等待线程被中断：恢复中断标记并把中断包装成异常上抛
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            if (error.get() != null) {
                // 订阅阶段出错：把原始异常包装后交给 CompletableFuture（join 时抛出）
                throw new RuntimeException(error.get());
            }
            // 组装完整消息：来源标记为当前 provider + model，便于追溯
            // 数据案例：provider="deepseek", model="glm-5.3-flash"
            //           → Message(role="assistant", source=ModelMessageSource["deepseek","glm-5.3-flash"],
            //                     content=[TextBlock("你好！有什么可以帮你？")])
            return assembler.message(new MessageSource.ModelMessageSource(options.provider(), options.model()));
        });
    }

}
