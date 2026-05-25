package com.group4.chatapp

import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Lớp khởi động chính của ứng dụng chat.
 *
 * Class này đánh dấu điểm vào của Spring Boot và bật cơ chế scheduling để
 * các tác vụ nền theo lịch có thể được thực thi trong suốt vòng đời ứng dụng.
 */
@SpringBootApplication
@EnableScheduling
class ChatApplication

/**
 * Khởi chạy ứng dụng Spring Boot.
 *
 * Hàm này dùng để tạo và cấu hình application context cho hệ thống chat,
 * đồng thời tắt banner mặc định khi ứng dụng khởi động.
 *
 * @param args Các tham số dòng lệnh được truyền vào lúc khởi động ứng dụng.
 */
fun main(args: Array<String>) {
    runApplication<ChatApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}
