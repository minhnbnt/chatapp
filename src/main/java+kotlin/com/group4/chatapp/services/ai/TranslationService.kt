package com.group4.chatapp.services.ai

import com.group4.chatapp.dtos.messages.MessageTranslateRequestDto
import com.group4.chatapp.dtos.messages.MessageTranslationDto
import com.group4.chatapp.exceptions.ApiException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Locale

/**
 * Service dịch tin nhắn sang ngôn ngữ khác bằng AI.
 *
 * Service này chuẩn hóa đầu vào, kiểm tra cache Redis, gọi AI khi cần và có
 * cơ chế fallback lỗi rõ ràng cho rate limit hoặc service unavailable.
 */
@Service
class TranslationService(
    private val openAIClientService: OpenAIClientService,
    private val promptService: PromptService,
    private val redisTemplate: StringRedisTemplate
) {

    /**
     * Dịch nội dung theo request của client.
     *
     * Behavior của method:
     * - Chuẩn hóa text, ngôn ngữ nguồn/đích và ngữ cảnh trước đó.
     * - Dùng cache Redis nếu đã có bản dịch tương ứng.
     * - Gọi AI để dịch nếu cache miss.
     * - Nếu AI lỗi, trả về lỗi phù hợp theo trạng thái hiện tại.
     *
     * @param dto Request chứa text và tham số dịch.
     * @return DTO chứa bản dịch và metadata ngôn ngữ.
     */
    fun translate(dto: MessageTranslateRequestDto): MessageTranslationDto {
        val text = normalizeText(dto.text())
        val sourceLanguage = normalizeSourceLanguage(dto.sourceLanguage())
        val targetLanguage = normalizeTargetLanguage(dto.targetLanguage())
        val previousMessages = normalizePreviousMessages(dto.previousMessages())
        val cacheKey =
            buildTranslationCacheKey(text, sourceLanguage, targetLanguage, previousMessages)

        getCachedTranslation(cacheKey, targetLanguage)?.let { return it }

        return try {

            val prompt = promptService.buildTranslationPrompt(
                text, sourceLanguage, targetLanguage,
                previousMessages
            )

            val translatedText = openAIClientService.requestText(
                prompt, "Translation service", 0.1
            )

            if (translatedText.isEmpty()) {
                throw ApiException(HttpStatus.BAD_GATEWAY, "AI result is empty")
            }

            var detectedSourceLanguage = sourceLanguage
            if (sourceLanguage == "auto") {
                detectedSourceLanguage = ""
            }

            MessageTranslationDto(translatedText, detectedSourceLanguage, targetLanguage)
                .also { cacheTranslation(cacheKey, it) }

        } catch (ex: Exception) {
            fallbackTranslation(ex)
        }
    }

    /**
     * Đọc bản dịch từ cache Redis nếu đã từng dịch trước đó.
     *
     * Behavior của method:
     * - Trả về DTO rút gọn nếu tìm thấy cache.
     * - Nếu Redis lỗi hoặc cache miss, trả về null.
     *
     * @param cacheKey Khóa cache.
     * @param targetLanguage Ngôn ngữ đích.
     * @return Bản dịch đã cache hoặc null.
     */
    private fun getCachedTranslation(cacheKey: String, targetLanguage: String): MessageTranslationDto? {
        return try {
            val translatedText = redisTemplate.opsForValue().get(TRANSLATION_CACHE_PREFIX + cacheKey)
            if (translatedText != null) {
                MessageTranslationDto(translatedText, "", targetLanguage)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lưu bản dịch vào cache Redis.
     *
     * Behavior của method:
     * - Lưu text dịch theo cache key.
     * - Gán TTL cho cache để tự hết hạn.
     * - Nếu Redis lỗi, chỉ ghi log cảnh báo.
     *
     * @param cacheKey Khóa cache.
     * @param dto DTO bản dịch cần cache.
     */
    private fun cacheTranslation(cacheKey: String, dto: MessageTranslationDto) {
        try {
            redisTemplate.opsForValue().set(
                TRANSLATION_CACHE_PREFIX + cacheKey,
                dto.translatedText,
                CACHE_TTL
            )
        } catch (e: Exception) {
            logger.warn("Failed to cache translation in Redis: {}", e.message)
        }
    }

    /**
     * Xử lý lỗi khi dịch thất bại.
     *
     * Behavior của method:
     * - Nếu bị rate limit, ném lỗi 429 với thông báo rõ ràng.
     * - Các lỗi khác được chuyển thành lỗi 502 với thông điệp chung.
     *
     * @param cause Exception gốc.
     * @return Không trả về giá trị; method luôn ném exception.
     */
    private fun fallbackTranslation(
        cause: Exception
    ): MessageTranslationDto {

        logger.warn("Translation AI fallback activated: {}", cause.message)

        if (cause is ApiException && cause.statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            throw ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Translation AI is rate limited, please try again shortly"
            )
        }

        throw ApiException(
            HttpStatus.BAD_GATEWAY,
            "Translation service is temporarily unavailable"
        )
    }

    /**
     * Chuẩn hóa danh sách tin nhắn ngữ cảnh.
     *
     * Behavior của method:
     * - Nếu danh sách rỗng, trả về empty list.
     * - Loại message rỗng, trim và cắt độ dài.
     * - Chỉ giữ tối đa số lượng context messages quy định.
     *
     * @param previousMessages Danh sách tin nhắn ngữ cảnh.
     * @return Danh sách ngữ cảnh đã chuẩn hóa.
     */
    private fun normalizePreviousMessages(previousMessages: List<String>?): List<String> {
        if (previousMessages.isNullOrEmpty()) {
            return emptyList()
        }

        val normalized = previousMessages.asSequence()
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(CONTEXT_TEXT_LIMIT) }
            .toList()

        if (normalized.isEmpty()) {
            return emptyList()
        }

        return normalized.takeLast(CONTEXT_MESSAGE_LIMIT)
    }

    /**
     * Tạo khóa cache cho bản dịch.
     *
     * @param text Nội dung gốc.
     * @param sourceLanguage Ngôn ngữ nguồn.
     * @param targetLanguage Ngôn ngữ đích.
     * @param previousMessages Ngữ cảnh trước đó.
     * @return Chuỗi khóa cache.
     */
    private fun buildTranslationCacheKey(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        previousMessages: List<String>
    ): String = buildString {
        append(targetLanguage)
        append('|')
        append(sourceLanguage)
        append('|')
        append(text)
        previousMessages.forEach {
            append('\u001F')
            append(it)
        }
    }

    /**
     * Chuẩn hóa text cần dịch.
     *
     * @param text Nội dung gốc.
     * @return Text đã trim.
     */
    private fun normalizeText(text: String?): String {
        val normalized = text?.trim().orEmpty()
        if (normalized.isEmpty()) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Text to translate is required")
        }
        return normalized
    }

    /**
     * Chuẩn hóa ngôn ngữ đích.
     *
     * @param language Mã ngôn ngữ.
     * @return Mã ngôn ngữ đã chuẩn hóa, mặc định `vi`.
     */
    private fun normalizeTargetLanguage(language: String?): String {
        val normalized = normalizeLanguageCode(language)
        return normalized.ifEmpty { "vi" }
    }

    /**
     * Chuẩn hóa ngôn ngữ nguồn.
     *
     * @param language Mã ngôn ngữ.
     * @return Mã ngôn ngữ đã chuẩn hóa, mặc định `auto`.
     */
    private fun normalizeSourceLanguage(language: String?): String {
        val normalized = normalizeLanguageCode(language)
        return normalized.ifEmpty { "auto" }
    }

    /**
     * Chuẩn hóa mã ngôn ngữ về dạng lowercase và thay `_` bằng `-`.
     *
     * @param language Giá trị thô.
     * @return Mã ngôn ngữ đã chuẩn hóa.
     */
    private fun normalizeLanguageCode(language: String?): String {
        val normalized = language
            ?.trim()
            ?.replace('_', '-')
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        if (normalized.isEmpty()) {
            return ""
        }

        return normalized.take(16)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranslationService::class.java)

        private const val TRANSLATION_CACHE_PREFIX = "translation:"
        private val CACHE_TTL = Duration.ofHours(24)
        private const val CONTEXT_MESSAGE_LIMIT = 8
        private const val CONTEXT_TEXT_LIMIT = 500
    }
}