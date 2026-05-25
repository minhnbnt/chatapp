package com.group4.chatapp.services

import com.group4.chatapp.models.User
import com.group4.chatapp.repositories.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Optional

/**
 * Service cache thông tin user trong Redis để giảm truy vấn database.
 *
 * Cache này được dùng cho các thao tác đọc user theo username và có cơ chế
 * fallback sang repository nếu Redis không khả dụng.
 */
@Service
class UserCacheService(
    private val userRepository: UserRepository,
    private val redisTemplate: StringRedisTemplate,
) {

    private val logger = LoggerFactory.getLogger(UserCacheService::class.java)

    private fun keyForUsername(username: String) = "auth:user:$username"

    /**
     * Lấy user từ cache, nếu không có thì fallback sang database.
     *
     * Behavior của method:
     * - Đọc hash Redis theo username.
     * - Nếu có dữ liệu hợp lệ, dựng lại một object `User` tối giản.
     * - Nếu cache miss hoặc Redis lỗi, truy vấn repository và cache lại kết quả.
     *
     * @param username Username cần tra cứu.
     * @return `Optional` chứa user nếu tìm thấy.
     */
    fun getCachedUser(username: String): Optional<User> {
        try {
            val key = keyForUsername(username)
            val cached = redisTemplate.opsForHash<String, String>().entries(key)
            if (cached.isNotEmpty() && cached.containsKey("id")) {
                val user = User.builder()
                    .id(cached["id"]!!.toLong())
                    .username(cached["username"] ?: username)
                    .displayName(cached["displayName"])
                    .build()
                return Optional.of(user)
            }
        } catch (e: Exception) {
            logger.debug("Redis unavailable for user cache lookup: {}", e.message)
        }

        val user = userRepository.findByUsername(username)
        user.ifPresent { cacheUser(it) }
        return user
    }

    /**
     * Ghi một user vào cache Redis.
     *
     * Behavior của method:
     * - Lưu id, username và displayName dưới dạng hash.
     * - Đặt TTL một giờ cho cache entry.
     * - Nếu Redis lỗi, chỉ ghi cảnh báo.
     *
     * @param user User cần cache.
     */
    fun cacheUser(user: User) {
        try {
            val key = keyForUsername(user.username)
            val ops = redisTemplate.opsForHash<String, String>()
            ops.put(key, "id", user.id.toString())
            ops.put(key, "username", user.username)
            ops.put(key, "displayName", user.displayName ?: "")
            redisTemplate.expire(key, Duration.ofHours(1))
        } catch (e: Exception) {
            logger.warn("Redis unavailable, skipping user cache for {}: {}", user.username, e.message)
        }
    }

    /**
     * Xóa cache user theo username.
     *
     * Behavior của method:
     * - Xóa key Redis tương ứng với username.
     * - Nếu Redis lỗi, chỉ ghi cảnh báo.
     *
     * @param username Username cần invalidate.
     */
    fun invalidateUserCache(username: String) {
        try {
            redisTemplate.delete(keyForUsername(username))
        } catch (e: Exception) {
            logger.warn("Redis unavailable, skipping user cache invalidation for {}: {}", username, e.message)
        }
    }
}