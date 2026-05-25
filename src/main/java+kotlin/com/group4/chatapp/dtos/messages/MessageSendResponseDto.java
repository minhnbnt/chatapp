package com.group4.chatapp.dtos.messages;

/**
 * DTO trả về sau khi gửi tin nhắn thành công.
 *
 * DTO này chỉ chứa id của tin nhắn vừa được tạo để client có thể tham chiếu.
 */
public record MessageSendResponseDto(long id) {}
