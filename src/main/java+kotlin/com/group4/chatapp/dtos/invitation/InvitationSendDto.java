package com.group4.chatapp.dtos.invitation;

import jakarta.validation.constraints.NotEmpty;
import org.jspecify.annotations.Nullable;

/**
 * DTO dùng để gửi lời mời đến người dùng khác.
 *
 * DTO này chứa username người nhận và room/group tùy chọn liên quan.
 */
public record InvitationSendDto(

    @NotEmpty
    String receiverUserName,

    @Nullable
    Long chatGroupId

) {}
