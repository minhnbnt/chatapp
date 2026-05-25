package com.group4.chatapp.dtos.chatbot;

/**
 * DTO báo lỗi trong luồng stream chatbot.
 *
 * Client dùng DTO này để hiển thị hoặc xử lý trạng thái thất bại của stream.
 */
public record ChatbotStreamErrorDto(String message) {}
