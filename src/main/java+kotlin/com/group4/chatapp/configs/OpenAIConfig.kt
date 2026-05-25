package com.group4.chatapp.configs

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Cấu hình client gọi dịch vụ AI ngôn ngữ.
 *
 * Class này tạo OpenAI client với base URL, API key và timeout lấy từ cấu hình
 * của ứng dụng để service có thể gửi yêu cầu sinh nội dung một cách thống nhất.
 */
@Configuration
class OpenAIConfig(
    @Value("\${agents.messages.api-key}") private val apiKey: String,
    @Value("\${agents.messages.base-url}") private val baseUrl: String,
    @Value("\${agents.messages.request-timeout-seconds:45}") private val timeout: Int
) {

    /**
     * Tạo client dùng để giao tiếp với dịch vụ AI nhắn tin.
     *
     * Behavior của method:
     * - Chuẩn hóa `baseUrl` và `apiKey` bằng cách loại bỏ khoảng trắng đầu/cuối.
     * - Ném lỗi cấu hình nếu thiếu base URL hoặc API key.
     * - Giới hạn timeout tối thiểu 10 giây để tránh cấu hình quá nhỏ.
     * - Bật cơ chế retry ở mức 3 lần cho các request thất bại tạm thời.
     *
     * @return OpenAI client được cấu hình sẵn cho ứng dụng.
     */
    @Bean
    fun openAIClient(): OpenAIClient {
        val normalizedBaseUrl = baseUrl.trim()
        check(normalizedBaseUrl.isNotEmpty()) { "Message AI service is not configured" }

        val normalizedApiKey = apiKey.trim()
        check(normalizedApiKey.isNotEmpty()) { "LLM_API_KEY is not configured" }

        return OpenAIOkHttpClient.builder()
            .apiKey(normalizedApiKey)
            .baseUrl(normalizedBaseUrl)
            .timeout(Duration.ofSeconds(maxOf(timeout, 10).toLong()))
            .maxRetries(3)
            .build()
    }
}