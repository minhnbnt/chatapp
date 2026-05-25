package com.group4.chatapp.dtos.group;

import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO dùng để cập nhật group chat bằng multipart form data.
 *
 * DTO này hỗ trợ vừa đổi tên nhóm vừa upload avatar mới trong cùng request.
 */
public record GroupChatUpdateMultipartDto(
    @Nullable String name,
    @Nullable MultipartFile avatar
) {
}
