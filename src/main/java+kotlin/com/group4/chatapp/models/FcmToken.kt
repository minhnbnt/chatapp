package com.group4.chatapp.models

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.sql.Timestamp

/**
 * Entity lưu FCM token của một người dùng trên một thiết bị.
 *
 * Mỗi bản ghi đại diện cho một token có thể được dùng để gửi push notification,
 * đồng thời theo dõi thời điểm tạo và lần sử dụng gần nhất để dọn dẹp token cũ.
 */
@Entity
@Table(name = "fcm_tokens", uniqueConstraints = [
    UniqueConstraint(columnNames = ["user_id", "token"])
])
class FcmToken(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false, length = 500)
    var token: String,

    @CreationTimestamp
    var createdAt: Timestamp? = null,

    @Column(name = "last_used")
    var lastUsed: Timestamp? = null
)