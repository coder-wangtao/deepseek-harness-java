package cn.wangtao.deepseek.harness.domain.model.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 注册表接受 {@link ToolExecution} 后，传递给工具实现的运行时上下文。
 * 对应 TS 侧 {@code ToolRunContext}。
 *
 * <p>工具是被动的能力提供方，它拿不到 Agent 循环、会话等宿主对象的引用；
 * 所有"工具想反向影响 Agent 循环"的动作都必须通过本上下文完成，
 * 这是工具影响循环的<b>唯一受控通道</b>（最小权限原则）。</p>
 *
 * <p>提供三类受控能力：</p>
 * <ul>
 *   <li>{@link #deferContext(Message)} — 延迟注入一条上下文消息。典型用途：
 *       SkillTool 加载技能后，把"后续步骤必须遵循的指令"延迟到工具结果之后注入，
 *       避免指令出现在工具结果内容块里被模型当成数据而非指令。</li>
 *   <li>{@link #recordArtifact(String)} — 登记工具产生的文件产物（绝对路径，去重）。
 *       典型用途：FsWriteTool / FsEditTool / StrReplaceEditorTool 写完文件后登记，
 *       ToolCallExecutor 在回合结束时 {@link #drainArtifacts()} 聚合，
 *       由 ReactLoopAgent.drainWrittenArtifacts 随 done 事件下发给前端展示。</li>
 *   <li>{@link #concludeTurn()} — 将本次工具结果标记为本回合的终止结果。
 *       适用于"提问/等待用户输入"这类工具：返回即代表本回合结束，
 *       不再继续下一轮模型请求。</li>
 * </ul>
 *
 * <p>本类执行期间可变（工作台语义），字段访问均做了并发防护：
 * deferredContexts / artifacts 用 synchronized 隔离，
 * turnConcluded 用 AtomicBoolean——工具可能在并行组中同时执行。</p>
 *
 * @author wangtao
 */
public final class ToolRunContext {

    /** 本次执行的受管身份（callId / rootCallId / token），由注册表分配，工具只读。 */
    private final ToolExecution execution;

    /** 延迟注入的上下文消息，由 ToolCallExecutor 在处理工具结果后 drain 合并进 additionalContexts。 */
    private final List<Message> deferredContexts = new ArrayList<>();

    /** 已登记的文件产物绝对路径（去重），回合结束时由执行器 drain 聚合。 */
    private final List<String> artifacts = new ArrayList<>();

    /** 回合终止标记；工具执行可能并行，用原子布尔保证"标记一次"语义。 */
    private final AtomicBoolean turnConcluded = new AtomicBoolean(false);

    /**
     * 创建工具执行上下文。
     * @param execution 本次执行的受管身份，由注册表通过 {@code ToolExecution.from(input)} 构造。
     */
    public ToolRunContext(ToolExecution execution) {
        this.execution = execution;
    }

    /** 本次执行的受管身份（只读视图），工具可从中读取 callId 等信息，不可修改。 */
    public ToolExecution execution() {
        return execution;
    }

    /**
     * 延迟一条上下文消息，供 Agent 循环在工具结果后注入。
     * <p>时机：ToolCallExecutor 处理本次结果时 {@link #drainDeferredContexts()} 取出，
     * 合并进 {@code ToolExecutionResult} 的 additionalContexts，再由循环作为消息接收。</p>
     * <p>数据示例：SkillTool 注入"请严格按照以下技能步骤执行"的指令消息。</p>
     * @param context 要延迟的上下文消息；null 直接忽略。
     */
    public void deferContext(Message context) {
        if (context != null) {
            deferredContexts.add(context);
        }
    }

    /**
     * 登记本次工具产生的文件产物（绝对路径，去重）。
     * <p>由写文件类工具调用（FsWriteTool / FsEditTool / StrReplaceEditorTool），
     * 执行器按回合聚合并随 done 事件下发给前端。</p>
     * @param path 产物绝对路径；null / 空白 / 重复登记均被忽略。
     */
    public void recordArtifact(String path) {
        if (path != null && !path.isBlank() && !artifacts.contains(path)) {
            artifacts.add(path);
        }
    }

    /**
     * 取走已登记的产物路径列表（取走即清空，take-and-clear 语义）。
     * <p>调用方：ToolCallExecutor 在每个工具结果聚合时调用，
     * ReactLoopAgent 再通过 drainWrittenArtifacts 汇总整个回合的产物。</p>
     * @return 本批次产物绝对路径列表；无产物时返回空列表。
     */
    public List<String> drainArtifacts() {
        synchronized (artifacts) {
            if (artifacts.isEmpty()) return List.of();
            List<String> copy = List.copyOf(artifacts);
            artifacts.clear();
            return copy;
        }
    }

    /**
     * 将当前工具结果标记为本回合的终止结果。
     * <p>适用场景：提问（ask）、等待用户输入类工具——它们的返回本身就是回合终点，
     * 调度器据此不再发起下一轮模型请求。</p>
     */
    public void concludeTurn() {
        turnConcluded.set(true);
    }

    /** 是否已调用过 {@link #concludeTurn()}。 */
    public boolean isTurnConcluded() {
        return turnConcluded.get();
    }

    /**
     * 取走全部延迟上下文消息。
     * <p>调用方：ToolCallExecutor 在处理工具结果、合并 additionalContexts 时调用。</p>
     * <p>实现说明：当前实现仅做快照拷贝、不清空源列表；
     * 每次执行结果只处理一次，快照语义满足现有调用链。</p>
     * @return 延迟上下文消息快照；无消息时返回空列表。
     */
    public List<Message> drainDeferredContexts() {
        synchronized (deferredContexts) {
            return List.copyOf(deferredContexts);
        }
    }
}
