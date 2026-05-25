package com.group4.chatapp.dtos.messages;

import com.group4.chatapp.dtos.AttachmentDto;
import com.group4.chatapp.dtos.user.UserWithAvatarDto;
import com.group4.chatapp.models.ChatMessage;
import com.group4.chatapp.models.User;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * DTO biểu diễn tin nhắn đã được gửi/nhận để trả về cho client.
 *
 * DTO này chứa sender, nội dung, attachments và danh sách người đã xem tin nhắn.
 */
public record MessageReceiveDto(
    long id,
    String sender,
    UserWithAvatarDto senderProfile,
    String message,
    Timestamp sentOn,
    List<AttachmentDto> attachments,
    List<UserWithAvatarDto> seenBy
) {

    /**
     * Tạo DTO từ entity ChatMessage.
     *
     * Behavior của method:
     * - Sao chép sender, nội dung và thời gian gửi.
     * - Chuyển attachments sang DTO và loại bỏ phần tử null.
     * - Khởi tạo seenBy rỗng.
     *
     * @param message Entity tin nhắn nguồn.
     */
    public MessageReceiveDto(ChatMessage message) {

        this(
            message.getId(),
            message.getSender().getUsername(),
            new UserWithAvatarDto(message.getSender()),
            message.getMessage(),
            message.getSentOn(),
            message.getAttachments()
                .stream()
                .filter(Objects::nonNull)
                .map(AttachmentDto::new)
                .toList(),
            List.of()
        );
    }

    /**
     * Tạo DTO từ entity ChatMessage kèm danh sách người đã xem.
     *
     * Behavior của method:
     * - Sao chép dữ liệu tin nhắn như constructor một tham số.
     * - Chuyển danh sách người đã xem sang DTO avatar.
     *
     * @param message Entity tin nhắn nguồn.
     * @param seenByUsers Danh sách user đã xem tin nhắn.
     */
    public MessageReceiveDto(ChatMessage message, List<User> seenByUsers) {

        this(
            message.getId(),
            message.getSender().getUsername(),
            new UserWithAvatarDto(message.getSender()),
            message.getMessage(),
            message.getSentOn(),
            message.getAttachments()
                .stream()
                .filter(Objects::nonNull)
                .map(AttachmentDto::new)
                .toList(),
            seenByUsers
                .stream()
                .filter(Objects::nonNull)
                .map(UserWithAvatarDto::new)
                .toList()
        );
    }
}
