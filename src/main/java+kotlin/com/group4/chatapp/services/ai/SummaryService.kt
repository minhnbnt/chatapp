package com.group4.chatapp.services.ai

import com.group4.chatapp.dtos.messages.MessageSummarizeRequestDto
import com.group4.chatapp.dtos.messages.MessageSummaryDto
import com.group4.chatapp.exceptions.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.Collections

/**
 * Service tóm tắt hội thoại bằng AI và fallback nội bộ khi cần.
 *
 * Service này chuẩn hóa dữ liệu đầu vào, cache kết quả summary, và có cơ chế
 * dự phòng để vẫn trả về nội dung hữu ích khi AI không sẵn sàng.
 */
@Service
class SummaryService(
    private val openAIClientService: OpenAIClientService,
    private val promptService: PromptService
) {

    /**
     * Tóm tắt danh sách tin nhắn của một phòng chat.
     *
     * Behavior của method:
     * - Chuẩn hóa danh sách tin nhắn và tên phòng.
     * - Trả về kết quả từ cache nếu đã có.
     * - Gọi AI để tạo summary khi cache miss.
     * - Nếu AI lỗi, dùng fallback summary nội bộ và cache kết quả.
     *
     * @param dto Request chứa các tin nhắn cần tóm tắt.
     * @return Summary DTO kèm số lượng tin nhắn đã xử lý.
     */
    fun summarize(dto: MessageSummarizeRequestDto): MessageSummaryDto {

        val summaryMessages = normalizeSummaryMessages(dto.messages())
        val roomName = normalizeRoomName(dto.roomName())
        val cacheKey = buildSummaryCacheKey(summaryMessages, roomName)

        summaryCache[cacheKey]?.let { return it }

        return try {

            val prompt = promptService.buildSummaryPrompt(summaryMessages, roomName)
            val summaryText = openAIClientService.requestText(
                prompt, "Summary service", 0.2
            )
            if (summaryText.isBlank()) {
                throw ApiException(HttpStatus.BAD_GATEWAY, "AI result is empty")
            }

            MessageSummaryDto(summaryText, summaryMessages.size)
                .also { summaryCache[cacheKey] = it }

        } catch (ex: Exception) {
            fallbackSummary(summaryMessages, roomName, cacheKey, ex)
        }
    }

    /**
     * Tạo summary dự phòng khi AI không hoạt động.
     *
     * Behavior của method:
     * - Ghi log nguyên nhân lỗi.
     * - Tạo bản recap ngắn từ các tin nhắn gần nhất.
     * - Cache kết quả fallback để tránh lặp lại xử lý.
     *
     * @param summaryMessages Danh sách tin nhắn đã chuẩn hóa.
     * @param roomName Tên phòng chat.
     * @param cacheKey Khóa cache summary.
     * @param cause Exception gốc gây fallback.
     * @return Summary DTO dạng fallback.
     */
    private fun fallbackSummary(
        summaryMessages: List<String>,
        roomName: String,
        cacheKey: String,
        cause: Exception
    ): MessageSummaryDto {
        logger.warn("Summary AI fallback activated: {}", cause.message)

        val previewCount = minOf(summaryMessages.size, 8)
        val startIndex = maxOf(summaryMessages.size - previewCount, 0)

        val text = buildString {
            append("AI summary is temporarily unavailable.\n")
            if (roomName.isNotBlank()) {
                append("Chat room: ")
                append(roomName)
                append('\n')
            }
            append("Recent messages: ")
            append(summaryMessages.size)
            append('\n')
            append("Quick recap:\n")
            for (index in startIndex until summaryMessages.size) {
                append("- ")
                append(compactFallbackLine(summaryMessages[index], 160))
                append('\n')
            }
            append("\nPlease try again in a few minutes for a richer AI summary.")
        }

        return MessageSummaryDto(text.trim(), summaryMessages.size)
            .also { summaryCache[cacheKey] = it }
    }

    /**
     * Rút gọn một dòng fallback để tránh quá dài.
     *
     * @param value Nội dung cần rút gọn.
     * @param maxLength Độ dài tối đa.
     * @return Chuỗi đã cắt gọn.
     */
    private fun compactFallbackLine(value: String?, maxLength: Int): String {
        val normalized = value?.replace('\n', ' ')?.trim().orEmpty()
        if (normalized.length <= maxLength) {
            return normalized
        }

        return normalized.substring(0, maxLength - 3).trim() + "..."
    }

    /**
     * Chuẩn hóa danh sách tin nhắn đầu vào cho summary.
     *
     * Behavior của method:
     * - Bắt buộc phải có ít nhất một tin nhắn.
     * - Loại chuỗi rỗng, trim và cắt độ dài mỗi message.
     * - Giới hạn số message lấy từ cuối danh sách.
     *
     * @param rawMessages Danh sách tin nhắn thô.
     * @return Danh sách tin nhắn đã chuẩn hóa.
     */
    private fun normalizeSummaryMessages(rawMessages: List<String>?): List<String> {
        if (rawMessages.isNullOrEmpty()) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Messages to summarize are required"
            )
        }

        val normalized = rawMessages.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(SUMMARY_TEXT_LIMIT) }
            .toList()

        if (normalized.isEmpty()) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "Messages to summarize are required"
            )
        }

        return normalized.takeLast(SUMMARY_MESSAGE_LIMIT)
    }

    /**
     * Chuẩn hóa tên phòng chat.
     *
     * @param roomName Tên phòng thô.
     * @return Tên phòng đã trim và giới hạn độ dài.
     */
    private fun normalizeRoomName(roomName: String?): String {
        val normalized = roomName?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return ""
        }

        return normalized.take(120)
    }

    /**
     * Tạo khóa cache cho summary.
     *
     * @param summaryMessages Danh sách tin nhắn đã chuẩn hóa.
     * @param roomName Tên phòng đã chuẩn hóa.
     * @return Chuỗi khóa cache.
     */
    private fun buildSummaryCacheKey(
        summaryMessages: List<String>,
        roomName: String
    ): String = buildString {
        append(roomName)
        summaryMessages.forEach {
            append('\u001F')
            append(it)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SummaryService::class.java)

        private const val SUMMARY_CACHE_MAX_SIZE = 1024
        private const val SUMMARY_MESSAGE_LIMIT = 40
        private const val SUMMARY_TEXT_LIMIT = 500
    }

    private val summaryCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, MessageSummaryDto>(SUMMARY_CACHE_MAX_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MessageSummaryDto>?): Boolean {
                return size > SUMMARY_CACHE_MAX_SIZE
            }
        }
    )
}