package com.group4.chatapp.dtos.messages;

import com.group4.chatapp.dtos.user.UserWithAvatarDto;

import java.sql.Timestamp;

/**
 * DTO biểu diễn sự kiện một người dùng đã đọc tin nhắn trong phòng chat.
 *
 * DTO này được dùng để broadcast trạng thái read receipt theo thời gian thực.
 */
public record MessageReadEventDto(
    long roomId,
    UserWithAvatarDto reader,
    Timestamp readAt
) {}
