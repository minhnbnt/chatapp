package com.group4.chatapp.dtos.messages;

/**
 * DTO biểu diễn trạng thái đang gõ của người dùng.
 *
 * Client gửi DTO này để thông báo cho phòng chat biết người dùng có đang gõ hay không.
 */
public record MessageTypingDto(boolean typing) {}
