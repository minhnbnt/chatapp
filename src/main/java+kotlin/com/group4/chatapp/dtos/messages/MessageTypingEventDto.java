package com.group4.chatapp.dtos.messages;

/**
 * DTO biểu diễn sự kiện typing được broadcast ra realtime.
 *
 * DTO này cho biết room nào đang có ai gõ và trạng thái typing hiện tại.
 */
public record MessageTypingEventDto(
    long roomId,
    String sender,
    boolean typing
) {}
