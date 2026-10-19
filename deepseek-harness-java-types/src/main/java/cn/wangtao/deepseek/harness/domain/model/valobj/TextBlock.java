package cn.wangtao.deepseek.harness.domain.model.valobj;

/**
 * 终端用户可见的纯文本内容块。
 * <p>最常见的块类型：用户输入的正文、模型的文字回复、工具结果的文本摘要都用它承载。</p>
 * <p>数据示例：用户发"帮我看下这个文件" → TextBlock(text="帮我看下这个文件")。</p>
 * @author wangtao
 */
public record TextBlock(String text) implements ContentBlock {}
