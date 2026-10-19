package cn.wangtao.deepseek.harness.domain.model.valobj;

/**
 * 附件服务持有的不可变附件元数据。
 *
 * @param data      图片字节内容；紧凑构造器里防御性 clone，
 *                  与集合字段 List.copyOf 是同一套不可变纪律（外部改不动内部状态）。
 * @param mimeType  MIME 类型，如 image/png。
 * @param width     图片宽度（像素）。
 * @param height    图片高度（像素）。
 * @author wangtao
 */
public record ImageAttachmentRef(byte[] data, String mimeType, int width, int height) {
    public ImageAttachmentRef {
        data = data == null ? new byte[0] : data.clone();
    }
}
