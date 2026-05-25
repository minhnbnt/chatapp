package com.group4.chatapp.dtos.chatbot;

/**
 * DTO đại diện cho một token được đẩy ra trong luồng stream chatbot.
 *
 * Mỗi event token sẽ mang một phần nhỏ của câu trả lời được sinh ra.
 */
public record ChatbotStreamTokenDto(String token) {}
