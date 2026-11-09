package cn.wangtao.deepseek.harness.infrastructure.adapter.llm;

import cn.wangtao.deepseek.harness.domain.llm.adapter.LlmAdapter;
import cn.wangtao.deepseek.harness.domain.llm.adapter.ResolvedRetryPolicy;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.*;
import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * InMemoryLlmRuntimePort 的重试语义验证
 * <p>
 * 运行时端口是 LLM 调用的统一入口，本节验证四条核心规则：
 * 1. 可重试错误在未吐出内容时自动重试，并下发 Retry 事件；
 * 2. 重试预算耗尽后下发带"已重试 N 次"的错误终态；
 * 3. 正常成功时以 Stop 终态收尾；
 * 4. 注册表语义：hasAdapter/listProviders/未注册路由报错。
 */
class InMemoryLlmRuntimePortTest {

    @Test
    void retriesRetryableFailureAndExposesRetryNumber() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ResolvedRetryPolicy policy = new ResolvedRetryPolicy.Normal(
                1, 10, 0, 2, List.of(LlmErrorCodes.SERVER)
        );
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter("test", new FakeAdapter(policy, () -> {
            if (attempts.incrementAndGet() == 1) {
                return failureChunks("upstream unavailable", LlmErrorCodes.SERVER);
            }
            return successChunks("ok");
        }));

        List<StreamChunk> chunks = collect(runtime.stream(options()));

        assertEquals(2, attempts.get());
        StreamChunk.Retry retry = assertInstanceOf(StreamChunk.Retry.class, chunks.get(0));
        assertEquals(1, retry.attempt());
        assertEquals(2, retry.maxAttempts());
        assertEquals("upstream unavailable", retry.failureMessage());
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(chunks.size() - 1));
        assertTrue(finish.reason() instanceof FinishReason.Stop);
    }

    @Test
    void reportsRetryCountWhenBudgetIsExhausted() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ResolvedRetryPolicy policy = new ResolvedRetryPolicy.Normal(
                1, 10, 0, 2, List.of(LlmErrorCodes.SERVER)
        );
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter("test", new FakeAdapter(policy, () -> {
            attempts.incrementAndGet();
            return failureChunks("upstream unavailable", LlmErrorCodes.SERVER);
        }));

        List<StreamChunk> chunks = collect(runtime.stream(options()));

        assertEquals(3, attempts.get()); // 首发 + 2 次重试
        assertEquals(2, chunks.stream().filter(StreamChunk.Retry.class::isInstance).count());
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(chunks.size() - 1));
        FinishReason.Error error = assertInstanceOf(FinishReason.Error.class, finish.reason());
        assertEquals("upstream unavailable（已重试 2 次）", error.failure().message());
    }

    @Test
    void registrySemantics() {
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        FakeAdapter adapter = new FakeAdapter(null, List::of);
        runtime.registerAdapter("test", adapter);

        assertTrue(runtime.hasAdapter("test"));
        assertEquals(1, runtime.listProviders().size());
        assertEquals("test", runtime.listProviders().get(0).id());

        runtime.unregisterAdapter("test");
        assertTrue(!runtime.hasAdapter("test"));

        // 同步接口 resolveModel：未注册时抛 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> runtime.resolveModel("test", "m"));
        // 注：stream() 对未注册 provider 走"Error 终态"路径，但 SubmissionPublisher
        // 在 close 之后才订阅会丢弃缓冲 item（JDK 语义），该竞态在文章中专门讲解。
    }

    // ── 测试脚手架 ──────────────────────────────────────────

    private GenerateOptions options() {
        return new GenerateOptions(
                "test", "test-model", null,
                List.of(Message.createUser(List.of(new TextBlock("hello")), new MessageSource.UserSource())),
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private List<StreamChunk> collect(Flow.Publisher<StreamChunk> publisher) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<StreamChunk> chunks = new CopyOnWriteArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onNext(StreamChunk item) {
                chunks.add(item);
            }
            @Override public void onError(Throwable throwable) {
                done.countDown();
            }
            @Override public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return chunks;
    }

    /** 可编程的假适配器：惰性同步发布，按脚本返回 chunk 序列。 */
    @FunctionalInterface
    private interface ChunkFactory {
        List<StreamChunk> get();
    }

    private static final class FakeAdapter implements LlmAdapter {
        private final ResolvedRetryPolicy policy;
        private final ChunkFactory factory;

        FakeAdapter(ResolvedRetryPolicy policy, ChunkFactory factory) {
            this.policy = policy;
            this.factory = factory;
        }

        @Override public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            List<StreamChunk> chunks = factory.get();
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                chunks.forEach(subscriber::onNext);
                subscriber.onComplete();
            };
        }

        @Override public List<LlmModelInfo> listModels(String provider) { return List.of(); }
        @Override public LlmResolvedModelInfo resolveModel(String provider, String model) {
            throw new UnsupportedOperationException();
        }
        @Override public LlmProviderInfo providerInfo(String provider) {
            return new LlmProviderInfo(provider, provider);
        }
        @Override public String providerId() { return "test"; }
        @Override public ResolvedRetryPolicy retryPolicy(String provider) { return policy; }
    }

    private static List<StreamChunk> successChunks(String text) {
        return List.of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, text),
                new StreamChunk.BlockEnd(0, new TextBlock(text)),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
    }

    private static List<StreamChunk> failureChunks(String message, String code) {
        return List.of(
                new StreamChunk.Finish(new FinishReason.Error(new LlmFailure(message, code)))
        );
    }
}
