package cn.wangtao.deepseek.harness.domain.model.valobj;

/**
 * 位图图片内容块（当前仅用于用户内容）。
 * <p>用途：用户消息携带图片（多模态输入）。图片本体与元数据拆开——
 * 本块只持有一个 {@link ImageAttachmentRef} 引用，字节不散落在消息模型里，
 * 便于附件服务统一存储与去重。</p>
 * @author wangtao
 */
public record ImageBlock(ImageAttachmentRef attachment) implements ContentBlock {}




// public final class ImageBlock extends java.lang.Record implements ContentBlock {
//   private final ImageAttachmentRef attachment;
//   public ImageBlock(ImageAttachmentRef attachment) {
//       this.attachment = attachment;
//   }
//   public ImageAttachmentRef attachment() { return attachment; }
//   // equals / hashCode / toString 基于 attachment 自动生成
// }