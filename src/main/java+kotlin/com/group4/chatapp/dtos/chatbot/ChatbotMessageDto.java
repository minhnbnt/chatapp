package com.group4.chatapp.dtos.chatbot;

import com.group4.chatapp.models.ChatbotMessage;

import java.sql.Timestamp;
import java.util.Locale;

/**
 * DTO biểu diễn một tin nhắn trong hội thoại chatbot.
 *
 * DTO này chuẩn hóa role thành chữ thường để client xử lý đồng nhất.
 */
public record ChatbotMessageDto(
    long id,
    String role,
    String content,
    Timestamp createdOn
) {

    /**
     * Tạo DTO từ entity message của chatbot.
     *
     * Behavior của method:
     * - Sao chép id, content và createdOn.
     * - Chuyển role sang chữ thường theo locale ROOT.
     *
     * @param message Entity tin nhắn nguồn.
     */
    public ChatbotMessageDto(ChatbotMessage message) {
        this(
            message.getId(),
            message.getRole().name().toLowerCase(Locale.ROOT),
            message.getContent(),
            message.getCreatedOn()
        );
    }
}
