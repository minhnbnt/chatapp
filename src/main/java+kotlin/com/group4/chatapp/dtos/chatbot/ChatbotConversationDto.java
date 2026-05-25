package com.group4.chatapp.dtos.chatbot;

import com.group4.chatapp.models.ChatbotConversation;
import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;

/**
 * DTO biểu diễn thông tin một cuộc hội thoại chatbot.
 *
 * DTO này chứa tiêu đề, preview, cờ MCP và thời điểm tạo/cập nhật để client
 * render danh sách hoặc màn hình chi tiết hội thoại.
 */
public record ChatbotConversationDto(
    long id,
    String title,
    String preview,
    boolean mcpEnabled,
    @Nullable String mcpSessionId,
    Timestamp createdOn,
    Timestamp updatedOn
) {

    /**
     * Tạo DTO từ entity hội thoại chatbot.
     *
     * Behavior của method:
     * - Sao chép các trường hiển thị từ conversation.
     * - Nếu preview null thì đổi thành chuỗi rỗng.
     *
     * @param conversation Entity hội thoại nguồn.
     * @param preview Nội dung preview của hội thoại, có thể null.
     */
    public ChatbotConversationDto(ChatbotConversation conversation, @Nullable String preview) {
        this(
            conversation.getId(),
            conversation.getTitle(),
            preview == null ? "" : preview,
            conversation.isMcpEnabled(),
            conversation.getMcpSessionId(),
            conversation.getCreatedOn(),
            conversation.getUpdatedOn()
        );
    }
}
