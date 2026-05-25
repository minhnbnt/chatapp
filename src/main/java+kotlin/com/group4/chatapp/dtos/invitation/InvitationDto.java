package com.group4.chatapp.dtos.invitation;


import com.group4.chatapp.dtos.user.UserWithAvatarDto;
import com.group4.chatapp.models.Invitation;
import org.jspecify.annotations.Nullable;

/**
 * DTO biểu diễn một lời mời kết bạn hoặc mời vào phòng chat.
 *
 * DTO này bao gồm người gửi, người nhận, phòng chat liên quan và trạng thái
 * hiện tại của lời mời.
 */
public record InvitationDto(

    long id,

    UserWithAvatarDto sender,
    UserWithAvatarDto receiver,

    @Nullable
    Long chatRoomId,

    Invitation.Status status
) {

    /**
     * Tạo DTO từ entity Invitation.
     *
     * Behavior của method:
     * - Chuyển sender/receiver sang DTO avatar.
     * - Lấy chatRoomId nếu lời mời gắn với phòng chat.
     * - Sao chép trạng thái lời mời.
     *
     * @param invitation Entity lời mời nguồn.
     */
    public InvitationDto(Invitation invitation) {

        this(
            invitation.getId(),
            new UserWithAvatarDto(invitation.getSender()),
            new UserWithAvatarDto(invitation.getReceiver()),

            invitation.getChatRoom() == null
                ? null
                : invitation.getChatRoom().getId(),

            invitation.getStatus()
        );
    }
}
