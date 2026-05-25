package com.group4.chatapp.dtos.invitation;


import com.group4.chatapp.dtos.ChatRoomDto;
import com.group4.chatapp.dtos.user.UserWithAvatarDto;
import com.group4.chatapp.models.Invitation;
import org.jspecify.annotations.Nullable;

/**
 * DTO biểu diễn lời mời kèm phòng chat mới được tạo.
 *
 * DTO này dùng khi phản hồi lời mời tạo ra một phòng chat mới và cần trả cả
 * thông tin lời mời lẫn thông tin phòng vừa sinh ra.
 */
public record InvitationWithNewRoomDto(

    long id,

    UserWithAvatarDto sender,
    UserWithAvatarDto receiver,

    @Nullable
    Long chatRoomId,

    Invitation.Status status,

    @Nullable
    ChatRoomDto chatRoomDto
) {

    /**
     * Tạo DTO từ invitation và phòng chat mới (nếu có).
     *
     * Behavior của method:
     * - Chuyển sender/receiver sang DTO.
     * - Sao chép chatRoomId và status từ invitation.
     * - Đính kèm phòng chat mới nếu được cung cấp.
     *
     * @param invitation Entity lời mời nguồn.
     * @param chatRoomDto Phòng chat mới tạo, có thể null.
     */
    public InvitationWithNewRoomDto(
        Invitation invitation,
        @Nullable ChatRoomDto chatRoomDto
    ) {

        this(
            invitation.getId(),
            new UserWithAvatarDto(invitation.getSender()),
            new UserWithAvatarDto(invitation.getReceiver()),

            invitation.getChatRoom() == null
                ? null
                : invitation.getChatRoom().getId(),

            invitation.getStatus(),
            chatRoomDto
        );
    }
}
