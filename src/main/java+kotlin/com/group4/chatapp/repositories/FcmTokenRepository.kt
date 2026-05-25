package com.group4.chatapp.repositories

import com.group4.chatapp.models.FcmToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository truy vấn và quản lý các bản ghi FCM token.
 *
 * Repository này phục vụ cho việc đăng ký token, tìm token theo user,
 * xóa token không hợp lệ và dọn dẹp các token cũ không còn được sử dụng.
 */
@Repository
interface FcmTokenRepository : JpaRepository<FcmToken, Long> {
    fun findByUserId(userId: Long): List<FcmToken>
    fun findAllByUserIdOrderByLastUsedDesc(userId: Long): List<FcmToken>
    fun findByUserIdAndToken(userId: Long, token: String): Optional<FcmToken>
    fun deleteByUserIdAndToken(userId: Long, token: String)
    fun findByToken(token: String): Optional<FcmToken>
    fun findByUserIdIn(userIds: List<Long>): List<FcmToken>
    fun deleteByLastUsedBefore(lastUsed: java.sql.Timestamp): Long
}