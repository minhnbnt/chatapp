package com.group4.chatapp.dtos.user

/**
 * DTO biểu diễn trạng thái bật/tắt thông báo đẩy của người dùng.
 *
 * Giá trị này được trả về để client biết hiện tại push notification đang được
 * cho phép hay bị tắt cho tài khoản đang đăng nhập.
 */
data class NotificationSettingsDto(
    val pushEnabled: Boolean
)