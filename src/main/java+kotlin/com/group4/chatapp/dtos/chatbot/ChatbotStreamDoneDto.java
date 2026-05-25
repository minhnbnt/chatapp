package com.group4.chatapp.dtos.chatbot;

/**
 * DTO báo hiệu quá trình stream chatbot đã hoàn tất.
 *
 * DTO này mang conversationId, assistantMessageId và toàn bộ nội dung cuối.
 */
public record ChatbotStreamDoneDto(
    long conversationId,
    long assistantMessageId,
    String content
) {}
