package com.group4.chatapp.services

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Tác vụ định kỳ dọn dẹp FCM token không còn hoạt động.
 *
 * Job này chạy theo lịch để gọi service xóa các token đã quá thời gian lưu giữ.
 */
@Component
class FcmTokenCleanupJob(
    private val fcmTokenService: FcmTokenService
) {

    /**
     * Xóa các token FCM đã không được sử dụng trong thời gian dài.
     *
     * Behavior của method:
     * - Chạy theo cron 6 giờ một lần.
     * - Gọi service để prune các token inactive.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    fun pruneInactiveTokens() {
        fcmTokenService.pruneInactiveTokens()
    }
}