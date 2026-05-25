package com.group4.chatapp.dtos.messages;

/**
 * DTO chứa kết quả dịch một đoạn văn bản.
 *
 * DTO này trả về nội dung đã dịch cùng metadata về ngôn ngữ nguồn và đích.
 */
public record MessageTranslationDto(
    String translatedText,
    String detectedSourceLanguage,
    String targetLanguage
) {}
