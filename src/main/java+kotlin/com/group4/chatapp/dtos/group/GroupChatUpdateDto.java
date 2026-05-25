package com.group4.chatapp.dtos.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * DTO dùng để cập nhật thông tin group chat.
 *
 * DTO này cho phép đổi tên nhóm và/hoặc thay avatar bằng attachment ID.
 */
@Schema(description = "Request to update group chat information")
public record GroupChatUpdateDto(

    @Schema(description = "New group name", example = "Updated Group Name")
    @Nullable
    @Size(min = 1, max = 100)
    String name,

    @Schema(description = "New group avatar attachment ID")
    @Nullable
    Long avatarId
) {}