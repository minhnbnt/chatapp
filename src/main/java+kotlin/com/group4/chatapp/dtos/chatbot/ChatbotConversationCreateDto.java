package com.group4.chatapp.dtos.chatbot;

import jakarta.validation.constraints.Size;

/**
 * DTO dùng để tạo cuộc hội thoại chatbot mới.
 *
 * Trường title là tiêu đề hiển thị cho cuộc hội thoại và có giới hạn độ dài.
 */
public record ChatbotConversationCreateDto(
    @Size(max = 120)
    String title
) {}
