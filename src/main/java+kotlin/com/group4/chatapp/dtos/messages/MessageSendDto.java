package com.group4.chatapp.dtos.messages;

import com.group4.chatapp.exceptions.ApiException;
import com.group4.chatapp.models.Attachment;
import com.group4.chatapp.models.ChatMessage;
import com.group4.chatapp.models.ChatRoom;
import com.group4.chatapp.models.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * DTO dùng để gửi hoặc sửa một tin nhắn.
 *
 * DTO này chứa nội dung text, replyTo và danh sách file đính kèm.
 */
@Getter
@AllArgsConstructor
public class MessageSendDto {

    @Nullable
    private Long replyTo;

    @Nullable
    private String message;

    @Nullable
    private List<MultipartFile> attachments;

    /**
     * Trả về danh sách attachments, nếu null thì trả list rỗng.
     *
     * @return Danh sách file đính kèm hoặc list rỗng.
     */
    public List<MultipartFile> attachmentsOrEmpty() {
        if (attachments == null) {
            return List.of();
        }
        return attachments;
    }

    /**
     * Chuyển DTO thành entity ChatMessage.
     *
     * Behavior của method:
     * - Gắn replyTo, room, sender, nội dung, trạng thái và attachment.
     * - Không tự kiểm tra nghiệp vụ ngoài việc map dữ liệu sang entity.
     *
     * @param replyTo Tin nhắn được reply tới, có thể null.
     * @param room Phòng chat chứa tin nhắn.
     * @param sender Người gửi.
     * @param attachments Danh sách attachment đã được lưu.
     * @param status Trạng thái tin nhắn.
     * @return ChatMessage entity đã được dựng.
     */
    public ChatMessage toMessage(
        @Nullable ChatMessage replyTo,
        ChatRoom room,
        User sender,
        List<Attachment> attachments,
        ChatMessage.Status status
    ) {

        return ChatMessage.builder()
            .replyTo(replyTo)
            .room(room)
            .sender(sender)
            .message(this.message)
            .status(status)
            .attachments(attachments)
            .build();
    }

    /**
     * Kiểm tra request gửi tin nhắn có hợp lệ theo nghiệp vụ hay không.
     *
     * Behavior của method:
     * - Từ chối trường hợp vừa không có nội dung text vừa không có file đính kèm.
     * - Ném `ApiException` với mã 400 nếu request rỗng nghiệp vụ.
     */
    public void validate() {
        if (
            CollectionUtils.isEmpty(attachments)
                && StringUtils.isEmpty(message)
        ) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "message and attachment mustn't be empty together!"
            );
        }
    }
}
