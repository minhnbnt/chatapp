package com.group4.chatapp.services

import com.group4.chatapp.models.FcmToken
import com.group4.chatapp.repositories.FcmTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Service quản lý FCM token của người dùng.
 *
 * Service này chịu trách nhiệm đăng ký token, cập nhật thời điểm sử dụng,
 * truy xuất token để gửi thông báo và dọn dẹp token cũ hoặc không hợp lệ.
 */
@Service
class FcmTokenService(
    private val fcmTokenRepository: FcmTokenRepository,
    private val userService: UserService
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FcmTokenService::class.java)
        private const val MAX_TOKENS_PER_USER = 3
        private const val TOKEN_RETENTION_DAYS = 30L
    }

    /**
     * Đăng ký hoặc cập nhật FCM token cho người dùng hiện tại.
     *
     * Behavior của method:
     * - Lấy user hiện tại từ ngữ cảnh xác thực.
     * - Nếu token đã tồn tại của chính user đó, cập nhật `lastUsed`.
     * - Nếu token đã tồn tại ở user khác, xóa bản ghi cũ rồi lưu lại cho user hiện tại.
     * - Sau khi lưu, giới hạn số token tối đa mỗi user.
     * - Nếu có lỗi, ghi log và ném lại exception.
     *
     * @param token FCM token được client gửi lên.
     */
    @Transactional
    fun registerToken(token: String) {
        try {
            val user = userService.getUserOrThrows()
            val userId = user.id
            val now = Timestamp.from(Instant.now())

            val existing = fcmTokenRepository.findByUserIdAndToken(userId, token)
            if (existing.isPresent) {
                val fcmToken = existing.get()
                fcmToken.lastUsed = now
                fcmTokenRepository.save(fcmToken)
                LOGGER.debug("Updated FCM token for user {}", userId)
            } else {
                fcmTokenRepository.findByToken(token).ifPresent {
                    fcmTokenRepository.delete(it)
                }

                fcmTokenRepository.save(FcmToken(
                    user = user,
                    token = token,
                    lastUsed = now
                ))
                LOGGER.debug("Registered new FCM token for user {}", userId)
            }

            pruneUserTokens(userId)
        } catch (e: Exception) {
            LOGGER.error("Failed to register FCM token: {}", e.message, e)
            throw e
        }
    }

    /**
     * Lấy danh sách token FCM của một user.
     *
     * Behavior của method:
     * - Trả về token theo thứ tự `lastUsed` mới nhất trước.
     * - Loại trùng token trong kết quả trả về.
     * - Nếu có lỗi truy xuất dữ liệu, trả về danh sách rỗng.
     *
     * @param userId ID của người dùng cần lấy token.
     * @return Danh sách token hợp lệ, không trùng.
     */
    fun getTokensForUser(userId: Long): List<String> {
        return try {
            fcmTokenRepository
                .findAllByUserIdOrderByLastUsedDesc(userId)
                .mapNotNull { it.token }
                .distinct()
        } catch (e: Exception) {
            LOGGER.error("Failed to retrieve FCM tokens for user {}: {}", userId, e.message)
            emptyList()
        }
    }

    /**
     * Lấy token FCM cho nhiều user cùng lúc.
     *
     * Behavior của method:
     * - Trả về map với key là userId và value là danh sách token của user đó.
     * - Nếu truy vấn thất bại, trả về map rỗng.
     *
     * @param userIds Danh sách user ID cần truy xuất token.
     * @return Map userId -> danh sách token.
     */
    fun getTokensForUsers(userIds: List<Long>): Map<Long, List<String>> {
        return try {
            fcmTokenRepository.findByUserIdIn(userIds)
                .groupBy({ it.user.id }, { it.token })
        } catch (e: Exception) {
            LOGGER.error("Failed to retrieve FCM tokens for users: {}", e.message)
            emptyMap()
        }
    }

    /**
     * Xóa các token FCM đã quá hạn lưu giữ.
     *
     * Behavior của method:
     * - Xác định mốc cắt theo số ngày retention.
     * - Xóa các token có `lastUsed` trước mốc này.
     * - Trả về số bản ghi đã xóa; nếu lỗi thì trả về 0.
     *
     * @return Số token đã bị xóa.
     */
    @Transactional
    fun pruneInactiveTokens(): Long {
        val cutoff = Timestamp.from(Instant.now().minus(TOKEN_RETENTION_DAYS, ChronoUnit.DAYS))
        return try {
            val deleted = fcmTokenRepository.deleteByLastUsedBefore(cutoff)
            if (deleted > 0) {
                LOGGER.info("Pruned {} inactive FCM tokens older than {} days", deleted, TOKEN_RETENTION_DAYS)
            }
            deleted
        } catch (e: Exception) {
            LOGGER.error("Failed to prune inactive FCM tokens: {}", e.message, e)
            0
        }
    }

    /**
     * Xóa một token không hợp lệ của user.
     *
     * Behavior của method:
     * - Xóa token theo cặp userId và token.
     * - Nếu gặp lỗi khi xóa, chỉ ghi log và không làm dừng luồng chính.
     *
     * @param userId ID người dùng sở hữu token.
     * @param token Token cần xóa.
     */
    fun deleteInvalidToken(userId: Long, token: String) {
        try {
            fcmTokenRepository.deleteByUserIdAndToken(userId, token)
            LOGGER.debug("Deleted invalid FCM token for user {}", userId)
        } catch (e: Exception) {
            LOGGER.error("Failed to delete invalid FCM token for user {}: {}", userId, e.message)
        }
    }

    /**
     * Giới hạn số token lưu cho một user.
     *
     * Behavior của method:
     * - Lấy danh sách token của user theo thứ tự lastUsed mới nhất.
     * - Nếu số token vượt quá giới hạn, xóa các token cũ nhất.
     *
     * @param userId ID người dùng cần prune token.
     */
    private fun pruneUserTokens(userId: Long) {
        val tokens = fcmTokenRepository.findAllByUserIdOrderByLastUsedDesc(userId)
        if (tokens.size <= MAX_TOKENS_PER_USER) {
            return
        }

        tokens.drop(MAX_TOKENS_PER_USER).forEach { stale ->
            fcmTokenRepository.delete(stale)
        }
        LOGGER.debug(
            "Pruned {} stale FCM token(s) for user {}",
            tokens.size - MAX_TOKENS_PER_USER,
            userId
        )
    }
}