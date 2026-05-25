package com.group4.chatapp.dtos.invitation;

import jakarta.validation.constraints.NotNull;

/**
 * DTO dùng để phản hồi một lời mời.
 *
 * Trường accept cho biết người dùng chấp nhận hay từ chối lời mời.
 */
public record InvitationReplyDto(@NotNull Boolean accept) {}
