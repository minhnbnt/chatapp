package com.group4.chatapp.dtos.messages;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO dùng để yêu cầu AI tóm tắt một đoạn hội thoại.
 *
 * DTO này chứa danh sách message và tên phòng chat để tạo ngữ cảnh cho summary.
 */
public record MessageSummarizeRequestDto(
    @Size(min = 1, max = 40)
    List<@Size(max = 500) String> messages,

    @Size(max = 120)
    String roomName
) {}
