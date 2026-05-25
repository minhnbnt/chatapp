package com.group4.chatapp.dtos.messages;

/**
 * DTO chứa kết quả tóm tắt tin nhắn.
 *
 * DTO này trả về nội dung summary và số lượng message đã được xử lý.
 */
public record MessageSummaryDto(
    String summary,
    int messageCount
) {}
