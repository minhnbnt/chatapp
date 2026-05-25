package com.group4.chatapp.dtos.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * DTO dùng để thêm thành viên vào group chat.
 *
 * DTO này mang danh sách userId cần được thêm vào nhóm.
 */
@Schema(description = "Request to add members to a group")
public record GroupMembersAddDto(

    @Schema(description = "List of user IDs to add")
    @NotNull
    @NotEmpty
    @Size(min = 1, message = "At least one user must be provided")
    List<Long> memberIds
) {}