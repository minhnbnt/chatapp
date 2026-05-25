package com.group4.chatapp.dtos;

import com.group4.chatapp.models.Attachment;

/**
 * DTO biểu diễn thông tin attachment để trả về cho client.
 *
 * DTO này chỉ giữ source và loại file của attachment, phục vụ hiển thị
 * và tham chiếu tài nguyên đã upload.
 */
public record AttachmentDto(
    String source,
    Attachment.FileType type
) {

    /**
     * Tạo DTO từ entity Attachment.
     *
     * Behavior của method:
     * - Sao chép source và loại file từ attachment entity.
     * - Dùng để chuyển đổi entity sang dữ liệu trả về API.
     *
     * @param attachment Attachment nguồn.
     */
    public AttachmentDto(Attachment attachment) {
        this(attachment.getSource(), attachment.getType());
    }
}
