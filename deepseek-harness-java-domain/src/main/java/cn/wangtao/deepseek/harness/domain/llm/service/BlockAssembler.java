package cn.wangtao.deepseek.harness.domain.llm.service;

import cn.wangtao.deepseek.harness.domain.model.entity.Message;
import cn.wangtao.deepseek.harness.domain.model.entity.MessageSource;
import cn.wangtao.deepseek.harness.domain.model.valobj.ContentBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.TextBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ReasoningBlock;
import cn.wangtao.deepseek.harness.domain.model.valobj.ToolCallBlock;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.FinishReason;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.StreamChunk;
import cn.wangtao.deepseek.harness.domain.llm.model.valobj.TokenUsage;

import java.util.*;

/**
 * 内容块组装器：把流式 chunk 累积成完整内容块与最终消息。
 *
 * <h2>设计目的</h2>
 * <p>
 * 流式协议里，一段完整的回答被拆成 BlockStart → 多个 *Delta → BlockEnd
 * 三类事件，且工具调用的 ID/名称/参数 JSON 都是分片到达的。
 * 本类是"拼图手"：按块序号归拢增量，BlockEnd 时冻结成品，
 * 最终拼出一条结构完整的 {@link Message}（文本块 + 推理块 + 工具调用块）。
 * 它是无状态使用、单次会话内累积的有状态对象——每次生成 new 一个。
 *
 * <h2>运行距离（一次完整组装）</h2>
 * <pre>
 *   push(BlockStart)   → 登记新块（记录序号与类型）
 *   push(TextDelta)*n  → 向对应块追加文本
 *   push(BlockEnd)     → 用完整内容块冻结该块（此后忽略迟到分片）
 *   message(source)    → 按登记顺序把所有块装配成 ContentBlock 列表 → 包成 Message
 * </pre>
 *
 * <h2>关键设计点</h2>
 * <ul>
 *   <li><b>乱序容忍</b>：Delta 先于 Start 到达时，{@link #ensure} 会自动补建块；</li>
 *   <li><b>首闭优先</b>：同一序号收到多个 BlockEnd 时只认第一个，迟到的分片全部忽略；</li>
 *   <li><b>截断保护</b>：finish 为 MaxTokens 时丢弃 tool-call 块——
 *       被截断的参数 JSON 必然执行失败，宁可不给工具调用也不能给坏的。</li>
 * </ul>
 *
 * @author wt
 */
public class BlockAssembler {

    /**
     * 一个尚在累积（或已冻结）的内容块。
     * <p>生命周期：登记（Start/ensure）→ 累积（Delta）→ 冻结（BlockEnd 设置 block）。</p>
     */
    private static final class PartialBlock {
        /** 块类型："text" / "reasoning" / "tool-call"。 */
        String blockType;
        /** 累积的文本内容（对 tool-call 块而言是参数 JSON 分片）。 */
        StringBuilder text = new StringBuilder();
        /** 工具调用 ID（分片到达，非空分片覆盖）。 */
        String toolCallId;
        /** 工具调用名（分片到达，非空分片覆盖）。 */
        String toolCallName;
        /** 由内容块结束事件设置；设置后冻结当前部分内容。 */
        ContentBlock block;

        PartialBlock(String blockType) {
            this.blockType = blockType;
        }
    }

    /** 块序号 → 累积中的块；LinkedHashMap 保持插入序。 */
    private final Map<Integer, PartialBlock> partials = new LinkedHashMap<>();
    /** 块序号的登记顺序（输出时按此顺序排列）。 */
    private final List<Integer> order = new ArrayList<>();
    /** 上游上报的 token 用量（Usage 事件写入）。 */
    private TokenUsage usage;
    /** 终态结束原因（Finish 事件写入）。 */
    private FinishReason finish;

    /**
     * 摄入一条流式事件，按事件类型分发处理。
     *
     * <p>运行距离：instanceof 判别事件类型 → 找到/补建对应块 →
     * 追加内容或冻结块 → Usage/Finish 直接记录字段。</p>
     *
     * @param chunk 流式事件（8 种之一）
     */
    public void push(StreamChunk chunk) {
        if (chunk instanceof StreamChunk.BlockStart bs) {
            // 已登记过则忽略重复 Start，未登记则登记新块
            if (!partials.containsKey(Integer.valueOf(bs.index()))) {
                order.add(Integer.valueOf(bs.index()));
                partials.put(Integer.valueOf(bs.index()), new PartialBlock(bs.blockType()));
            }
        } else if (chunk instanceof StreamChunk.TextDelta td) {
            PartialBlock partial = ensure(td.index(), "text");
            if (partial.block != null) return; // closed; ignore stragglers
            partial.text.append(td.text());
        } else if (chunk instanceof StreamChunk.ReasoningDelta rd) {
            PartialBlock partial = ensure(rd.index(), "reasoning");
            if (partial.block != null) return;
            partial.text.append(rd.text());
        } else if (chunk instanceof StreamChunk.ToolCallDelta tcd) {
            PartialBlock partial = ensure(tcd.index(), "tool-call");
            if (partial.block != null) return;
            // ID/名称非空分片覆盖（首个分片一般就带全），参数 JSON 逐片追加
            if (tcd.id() != null && !tcd.id().isEmpty()) partial.toolCallId = tcd.id();
            if (tcd.name() != null && !tcd.name().isEmpty()) partial.toolCallName = tcd.name();
            partial.text.append(tcd.argumentsDelta());
        } else if (chunk instanceof StreamChunk.BlockEnd be) {
            PartialBlock partial = ensure(be.index(), be.block() != null ? be.block().getClass().getSimpleName() : "unknown");
            if (partial.block != null) return; // first close wins
            partial.block = be.block();
        } else if (chunk instanceof StreamChunk.Usage u) {
            this.usage = u.usage();
        } else if (chunk instanceof StreamChunk.Finish f) {
            this.finish = f.reason();
        }
        // Retry 事件是运行时端口发给下游的通知，组装器无需处理
    }

    /**
     * 按序号取块，不存在则补建（容忍 Delta 先于 Start 到达的乱序流）。
     *
     * @param index     块序号
     * @param blockType 补建时使用的块类型
     * @return 对应的累积块
     */
    private PartialBlock ensure(int index, String blockType) {
        PartialBlock partial = partials.get(Integer.valueOf(index));
        if (partial == null) {
            partial = new PartialBlock(blockType);
            partials.put(Integer.valueOf(index), partial);
            order.add(Integer.valueOf(index));
        }
        return partial;
    }

    /**
     * 把一个累积块装配成最终内容块。
     *
     * <p>运行距离：已冻结直接返回成品 → 未冻结按块类型现场构建
     * （tool-call 缺 ID/名称时补兜底值 call-{index} / 空串）→
     * 未知类型抛异常（防御流协议被破坏）。</p>
     *
     * @param partial 累积块
     * @param index   块序号（用于生成兜底工具调用 ID）
     * @return 完整内容块
     */
    private ContentBlock assemble(PartialBlock partial, int index) {
        if (partial.block != null) return partial.block;
        switch (partial.blockType) {
            case "text", "TextBlock" -> {
                return new TextBlock(partial.text.toString());
            }
            case "reasoning", "ReasoningBlock" -> {
                return new ReasoningBlock(partial.text.toString());
            }
            case "tool-call", "ToolCallBlock" -> {
                return new ToolCallBlock(
                        partial.toolCallId != null ? partial.toolCallId : "call-" + index,
                        partial.toolCallName != null ? partial.toolCallName : "",
                        partial.text.toString()
                );
            }
            default -> throw new IllegalStateException(
                    "cannot assemble incomplete block of type \"" + partial.blockType + "\"");
        }
    }

    /**
     * 输出所有内容块（按登记顺序）。
     *
     * <p>截断保护：finish 为 {@link FinishReason.MaxTokens} 时丢弃
     * tool-call 块——参数 JSON 被截断后必然无法解析执行，
     * 下游拿到只会报错，直接不给更安全。</p>
     *
     * @return 完整内容块列表
     */
    public List<ContentBlock> blocks() {
        List<ContentBlock> all = new ArrayList<>();
        for (int index : order) {
            PartialBlock partial = partials.get(Integer.valueOf(index));
            if (partial == null) throw new IllegalStateException(
                    "BlockAssembler invariant violated: no partial for index " + index);
            all.add(assemble(partial, index));
        }
        // max-tokens truncation: drop tool-call blocks
        if (finish != null && finish instanceof FinishReason.MaxTokens) {
            List<ContentBlock> kept = new ArrayList<>();
            for (ContentBlock block : all) {
                if (!(block instanceof ToolCallBlock)) {
                    kept.add(block);
                }
            }
            return kept;
        }
        return all;
    }

    /**
     * 读取 token 用量（上游未上报时为 null）。
     *
     * @return token 用量统计
     */
    public TokenUsage usage() {
        return usage;
    }

    /**
     * 读取结束原因；流未给 Finish 事件时按正常结束兜底。
     *
     * @return 结束原因（永不返回 null）
     */
    public FinishReason finish() {
        return finish != null ? finish : new FinishReason.Stop();
    }

    /**
     * 用累积结果组装一条完整的 assistant 消息。
     *
     * <p>运行距离：blocks() 装配全部内容块 → 生成随机消息 ID →
     * 绑定来源（未提供时兜底为 PluginSource）→ 返回 Message。</p>
     *
     * @param source 消息来源（记录 provider/model，用于追溯）
     * @return 完整的 assistant 消息
     */
    public Message message(MessageSource source) {
        return new Message(
                java.util.UUID.randomUUID().toString(),
                "assistant",
                blocks(),
                source != null ? source : new MessageSource.PluginSource("dsh-llm/assembler")
        );
    }
}
