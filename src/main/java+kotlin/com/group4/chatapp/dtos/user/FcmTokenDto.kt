package com.group4.chatapp.dtos.user

import jakarta.validation.constraints.NotBlank

/**
 * DTO chứa FCM token được client đăng ký cho thiết bị hiện tại.
 *
 * Token này được dùng để gửi thông báo đẩy tới đúng thiết bị của người dùng.
 */
data class FcmTokenDto(
    @field:NotBlank val token: String
)