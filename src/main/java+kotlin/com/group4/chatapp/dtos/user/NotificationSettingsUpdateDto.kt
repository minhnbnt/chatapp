package com.group4.chatapp.dtos.user

/**
 * DTO dùng để cập nhật trạng thái thông báo đẩy của người dùng.
 *
 * Client gửi giá trị này khi muốn bật hoặc tắt push notification cho tài khoản.
 */
data class NotificationSettingsUpdateDto(
    val pushEnabled: Boolean
)