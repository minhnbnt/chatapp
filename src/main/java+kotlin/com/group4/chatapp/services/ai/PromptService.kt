package com.group4.chatapp.services.ai

import org.springframework.stereotype.Service

/**
 * Service dựng prompt cho các tác vụ AI trong ứng dụng.
 *
 * Class này chuẩn hóa cách tạo prompt cho dịch thuật và tóm tắt để giữ
 * hành vi đầu vào ổn định cho backend AI.
 */
@Service
class PromptService {

    /**
     * Tạo prompt cho tác vụ dịch tin nhắn.
     *
     * Behavior của method:
     * - Chuẩn hóa ngôn ngữ đích, mặc định là tiếng Việt.
     * - Thêm gợi ý ngôn ngữ nguồn nếu client cung cấp.
     * - Đưa toàn bộ ngữ cảnh tin nhắn trước đó vào prompt theo thứ tự cũ -> mới.
     * - Trả về prompt gồm system prompt và user prompt.
     *
     * @param text Nội dung cần dịch.
     * @param sourceLanguage Mã ngôn ngữ nguồn hoặc `auto`.
     * @param targetLanguage Mã ngôn ngữ đích.
     * @param previousMessages Các tin nhắn ngữ cảnh trước đó.
     * @return PromptSpec chứa system/user prompt.
     */
    fun buildTranslationPrompt(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        previousMessages: List<String>
    ): PromptSpec {
        val normalizedTargetLanguage = targetLanguage.trim().ifEmpty { "vi" }
        val targetLanguageName = languageDisplayName(normalizedTargetLanguage)

        val userPrompt = buildString {
            append("Target language: ")
            append(targetLanguageName)
            append(" (code: ")
            append(normalizedTargetLanguage)
            append(")\n\n")

            if (sourceLanguage != "auto") {
                append("Source language hint: ")
                append(sourceLanguage)
                append("\n\n")
            }

            if (previousMessages.isNotEmpty()) {
                append("Conversation context (oldest to newest):\n")
                previousMessages.forEachIndexed { index, message ->
                    append(index + 1)
                    append(". ")
                    append(message)
                    append('\n')
                }
                append('\n')
            }

            append("Message to translate:\n")
            append(text)
        }

        return PromptSpec(
            systemPrompt = buildTranslationSystemPrompt(
                targetLanguageName,
                normalizedTargetLanguage
            ),
            userPrompt = userPrompt
        )
    }

    /**
     * Đổi mã ngôn ngữ sang tên hiển thị dễ đọc.
     *
     * @param code Mã ngôn ngữ.
     * @return Tên ngôn ngữ hiển thị hoặc chuỗi mô tả mặc định.
     */
    private fun languageDisplayName(code: String): String {
        return when (code.lowercase().substringBefore('-')) {
            "vi" -> "Vietnamese"
            "en" -> "English"
            "zh" -> "Chinese"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "fr" -> "French"
            "de" -> "German"
            "es" -> "Spanish"
            "pt" -> "Portuguese"
            "it" -> "Italian"
            "ru" -> "Russian"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            "th" -> "Thai"
            "id" -> "Indonesian"
            "ms" -> "Malay"
            "tr" -> "Turkish"
            "nl" -> "Dutch"
            "pl" -> "Polish"
            "sv" -> "Swedish"
            else -> "the language identified by code '$code'"
        }
    }

    /**
     * Tạo system prompt dùng cho tác vụ dịch.
     *
     * Prompt này yêu cầu model chỉ trả về bản dịch cuối cùng và không thêm mô tả.
     */
    private fun buildTranslationSystemPrompt(
        targetLanguageName: String,
        targetLanguageCode: String
    ): String {
        return """
            You translate chat messages into natural $targetLanguageName.
            Preserve meaning, tone, names, emoji, links, and message formatting.
            If the input is already in $targetLanguageName, rewrite it only if needed to sound natural.
            Output rules:
            - Return only the final translated text in $targetLanguageName (code: $targetLanguageCode).
            - Do not add explanations, notes, labels, prefixes, or suffixes.
            - Do not wrap the answer in quotes.
            - Do not use markdown code fences.
            - Do not mention the source language.
        """.trimIndent()
    }

    /**
     * Tạo prompt cho tác vụ tóm tắt hội thoại.
     *
     * Behavior của method:
     * - Nếu có tên phòng, thêm tên phòng vào ngữ cảnh.
     * - Liệt kê tin nhắn theo thứ tự cũ -> mới.
     * - Trả về prompt yêu cầu model chỉ tạo phần summary.
     *
     * @param summaryMessages Danh sách tin nhắn cần tóm tắt.
     * @param roomName Tên phòng chat.
     * @return PromptSpec cho summary.
     */
    fun buildSummaryPrompt(summaryMessages: List<String>, roomName: String): PromptSpec {
        val userPrompt = buildString {
            if (roomName.isNotBlank()) {
                append("Chat room: ")
                append(roomName)
                append("\n\n")
            }

            append("Recent messages (oldest to newest):\n")
            summaryMessages.forEachIndexed { index, message ->
                append(index + 1)
                append(". ")
                append(message)
                append('\n')
            }
        }

        return PromptSpec(
            systemPrompt = SUMMARY_SYSTEM_PROMPT,
            userPrompt = userPrompt
        )
    }

    /**
     * Cấu trúc prompt gồm system prompt và user prompt.
     */
    data class PromptSpec(
        val systemPrompt: String,
        val userPrompt: String
    )

    companion object {
        private const val SUMMARY_SYSTEM_PROMPT = """
            You summarize chat conversations in Vietnamese.
            Keep key facts, tasks, decisions, blockers, and follow-up actions.
            Output rules:
            - Return only the summary body, with no intro sentence and no closing sentence.
            - Do not use markdown code fences.
            - Do not add titles like "Tom tat" or "Summary".
            - Use short paragraphs.
            - Use plain bullet lines starting with "-" only when they improve clarity.
            - Do not invent facts that are not present in the messages.
        """
    }
}