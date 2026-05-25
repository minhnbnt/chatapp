package com.group4.chatapp.services

import com.group4.chatapp.exceptions.ApiException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * Service phát hành, kiểm tra và thu hồi token reset mật khẩu.
 *
 * Token được lưu trong Redis theo cả chiều token->username và username->token
 * để hỗ trợ xác thực và vô hiệu hóa nhanh chóng.
 */
@Service
class PasswordResetTokenService(
    private val redisTemplate: StringRedisTemplate,
) {

    @Value("\${password-reset.token-expiry-minutes:30}")
    private var tokenExpiryMinutes: Long = 30

    private val logger = LoggerFactory.getLogger(PasswordResetTokenService::class.java)
    private val secureRandom = SecureRandom()

    private fun tokenKey(token: String) = "auth:password-reset:token:$token"
    private fun usernameKey(username: String) = "auth:password-reset:user:$username"

    /**
     * Tạo token reset mật khẩu mới cho một username.
     *
     * Behavior của method:
     * - Thu hồi token cũ của user nếu có.
     * - Sinh token ngẫu nhiên đủ mạnh bằng `SecureRandom`.
     * - Lưu mapping token -> username và username -> token trong Redis với TTL.
     * - Nếu Redis lỗi, ném `ApiException` với trạng thái 503.
     *
     * @param username Username cần cấp token reset.
     * @return Chuỗi token mới.
     */
    fun generateToken(username: String): String {
        try {
            revokeByUsername(username)

            val raw = ByteArray(32)
            secureRandom.nextBytes(raw)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
            val ttl = Duration.ofMinutes(tokenExpiryMinutes)

            redisTemplate.opsForValue().set(tokenKey(token), username, ttl)
            redisTemplate.opsForValue().set(usernameKey(username), token, ttl)

            return token
        } catch (e: Exception) {
            logger.warn("Redis unavailable, failed to issue password reset token for user={}: {}", username, e.message)
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Password reset is temporarily unavailable")
        }
    }

    /**
     * Kiểm tra token reset mật khẩu còn hợp lệ hay không.
     *
     * Behavior của method:
     * - Tra cứu username theo token trong Redis.
     * - Nếu token không tồn tại hoặc đã hết hạn, ném lỗi 400.
     * - Nếu Redis không khả dụng, ném lỗi 503.
     *
     * @param token Token reset mật khẩu cần kiểm tra.
     * @return Username gắn với token hợp lệ.
     */
    fun validateToken(token: String): String {
        val username = try {
            redisTemplate.opsForValue().get(tokenKey(token))
        } catch (e: Exception) {
            logger.warn("Redis unavailable, failed to validate password reset token: {}", e.message)
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Password reset is temporarily unavailable")
        }

        if (username.isNullOrBlank()) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token")
        }

        return username
    }

    /**
     * Thu hồi một token reset mật khẩu cụ thể.
     *
     * Behavior của method:
     * - Xóa key token và key username liên quan khỏi Redis.
     * - Nếu Redis lỗi, chỉ ghi log cảnh báo.
     *
     * @param token Token cần revoke.
     */
    fun revokeToken(token: String) {
        try {
            val key = tokenKey(token)
            val username = redisTemplate.opsForValue().get(key)
            redisTemplate.delete(key)
            if (!username.isNullOrBlank()) {
                redisTemplate.delete(usernameKey(username))
            }
        } catch (e: Exception) {
            logger.warn("Redis unavailable, failed to revoke password reset token: {}", e.message)
        }
    }

    /**
     * Thu hồi token reset mật khẩu theo username.
     *
     * Behavior của method:
     * - Tra cứu token hiện có của user.
     * - Xóa cả mapping token và mapping username.
     * - Nếu Redis lỗi, chỉ ghi log cảnh báo.
     *
     * @param username Username cần revoke token.
     */
    fun revokeByUsername(username: String) {
        try {
            val existingToken = redisTemplate.opsForValue().get(usernameKey(username))
            if (!existingToken.isNullOrBlank()) {
                redisTemplate.delete(tokenKey(existingToken))
            }
            redisTemplate.delete(usernameKey(username))
        } catch (e: Exception) {
            logger.warn("Redis unavailable, failed to revoke password reset token by username={}: {}", username, e.message)
        }
    }
}