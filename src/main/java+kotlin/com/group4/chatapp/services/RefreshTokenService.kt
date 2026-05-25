package com.group4.chatapp.services

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Service quản lý refresh token thông qua Redis.
 *
 * Mỗi refresh token được gắn với một `jti` để server có thể kiểm tra tồn tại,
 * lưu metadata và revoke token khi cần.
 */
@Service
class RefreshTokenService(
    private val redisTemplate: StringRedisTemplate,
) {

    private val logger = LoggerFactory.getLogger(RefreshTokenService::class.java)

    private fun keyForJti(jti: String) = "auth:refresh:jti:$jti"

    /**
     * Lưu refresh token vào Redis theo `jti`.
     *
     * Behavior của method:
     * - Lưu username và thời điểm tạo token vào hash Redis.
     * - Gán TTL cho key bằng thời lượng được truyền vào.
     * - Nếu Redis lỗi, chỉ ghi cảnh báo và bỏ qua việc lưu.
     *
     * @param jti JWT ID của refresh token.
     * @param username Username sở hữu token.
     * @param ttl Thời gian sống của token trong Redis.
     */
    fun storeRefreshToken(jti: String, username: String, ttl: Duration) {
        try {
            val key = keyForJti(jti)
            redisTemplate.opsForHash<String, String>().put(key, "username", username)
            redisTemplate.opsForHash<String, String>().put(key, "created_at", System.currentTimeMillis().toString())
            redisTemplate.expire(key, ttl)
        } catch (e: Exception) {
            logger.warn("Redis unavailable, skipping refresh token storage for jti={}: {}", jti, e.message)
        }
    }

    /**
     * Kiểm tra refresh token theo `jti` còn hợp lệ hay không.
     *
     * Behavior của method:
     * - Kiểm tra key trong Redis còn tồn tại hay không.
     * - Nếu key không còn, coi như token đã bị revoke hoặc hết hạn.
     * - Nếu Redis lỗi, trả về `true` để không chặn luồng xác thực tạm thời.
     *
     * @param jti JWT ID của refresh token.
     * @return `true` nếu token tồn tại theo Redis, ngược lại `false`.
     */
    fun isValidRefreshToken(jti: String): Boolean {
        return try {
            val exists = redisTemplate.hasKey(keyForJti(jti)) == true
            if (!exists) {
                logger.warn("Refresh token jti={} not found in Redis (revoked or expired)", jti)
            }
            exists
        } catch (e: Exception) {
            logger.warn("Redis unavailable, allowing refresh token jti={} anyway: {}", jti, e.message)
            true
        }
    }

    /**
     * Thu hồi refresh token theo `jti`.
     *
     * Behavior của method:
     * - Xóa key Redis của refresh token nếu tồn tại.
     * - Nếu Redis lỗi, chỉ ghi log cảnh báo.
     *
     * @param jti JWT ID của refresh token cần revoke.
     */
    fun revokeRefreshToken(jti: String) {
        try {
            redisTemplate.delete(keyForJti(jti))
        } catch (e: Exception) {
            logger.warn("Redis unavailable, skipping refresh token revocation for jti={}: {}", jti, e.message)
        }
    }
}