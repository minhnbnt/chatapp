package com.group4.chatapp.services

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * Service lưu và đọc tùy chọn bật/tắt thông báo đẩy của người dùng.
 *
 * Dữ liệu preference được lưu trong Redis để truy cập nhanh và có thể dùng làm
 * trạng thái mặc định khi gửi push notification.
 */
@Service
class NotificationPreferenceService(
    private val redisTemplate: StringRedisTemplate,
) {

    private val logger = LoggerFactory.getLogger(NotificationPreferenceService::class.java)

    /**
     * Tạo key Redis cho một user.
     *
     * @param userId ID người dùng.
     * @return Redis key lưu trạng thái push notification.
     */
    private fun keyForUser(userId: Long) = "prefs:notifications:push:$userId"

    /**
     * Kiểm tra user có bật push notification hay không.
     *
     * Behavior của method:
     * - Đọc giá trị từ Redis theo userId.
     * - Nếu chưa có dữ liệu, mặc định trả về `true`.
     * - Nếu Redis lỗi, cũng mặc định trả về `true` để không chặn thông báo.
     *
     * @param userId ID người dùng cần kiểm tra.
     * @return `true` nếu push được bật hoặc không đọc được cấu hình.
     */
    fun isPushEnabled(userId: Long): Boolean {
        return try {
            val value = redisTemplate.opsForValue().get(keyForUser(userId))
            value?.toBooleanStrictOrNull() ?: true
        } catch (e: Exception) {
            logger.warn("Redis unavailable, defaulting push notifications to enabled for user {}: {}", userId, e.message)
            true
        }
    }

    /**
     * Lưu trạng thái bật/tắt push notification cho user.
     *
     * Behavior của method:
     * - Ghi giá trị boolean vào Redis dưới dạng chuỗi.
     * - Nếu Redis lỗi, chỉ ghi log và không ném lỗi ra ngoài.
     *
     * @param userId ID người dùng cần cập nhật.
     * @param enabled Trạng thái mới của push notification.
     */
    fun setPushEnabled(userId: Long, enabled: Boolean) {
        try {
            redisTemplate.opsForValue().set(keyForUser(userId), enabled.toString())
        } catch (e: Exception) {
            logger.warn("Redis unavailable, cannot persist push notification preference for user {}: {}", userId, e.message)
        }
    }
}