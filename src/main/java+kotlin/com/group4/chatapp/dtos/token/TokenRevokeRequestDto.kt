package com.group4.chatapp.dtos.token

import jakarta.validation.constraints.NotBlank

/**
 * DTO dùng để yêu cầu thu hồi refresh token.
 *
 * Client gửi giá trị refresh token cần revoke để server xóa quyền sử dụng token đó.
 */
data class TokenRevokeRequestDto(
    @field:NotBlank
    val refresh: String,
)