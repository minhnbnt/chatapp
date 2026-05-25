package com.group4.chatapp.services.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.group4.chatapp.exceptions.ApiException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Client service gọi backend LLM tương thích OpenAI.
 *
 * Service này tự xây request/parse response, hỗ trợ fallback model khi gặp
 * rate limit, và chuẩn hóa cách gọi AI cho translation/summary.
 */
@Service
class OpenAIClientService(
    @Value("\${agents.messages.base-url:}") private val llmBaseUrl: String,
    @Value("\${agents.messages.model:}") private val llmModel: String,
    @Value("\${agents.messages.fallback-model:kilo-auto/free}")
    private val llmFallbackModel: String,
    @Value("\${agents.messages.api-key:}") private val llmApiKey: String,
    @Value("\${agents.messages.request-timeout-seconds:45}") private val llmRequestTimeoutSeconds: Int
) {

    companion object {
        private val logger = LoggerFactory.getLogger(OpenAIClientService::class.java)
        private val httpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val objectMapper = ObjectMapper()

    /**
     * Gửi yêu cầu sinh text tới dịch vụ AI.
     *
     * Behavior của method:
     * - Thử model chính trước, sau đó dùng model fallback nếu gặp rate limit.
     * - Nếu dịch vụ trả về lỗi khác rate limit, ném lỗi ngay.
     * - Nếu phản hồi không có text hợp lệ, ném `BAD_GATEWAY`.
     *
     * @param prompt Prompt gồm system/user message.
     * @param serviceName Tên logical của service gọi AI để đưa vào log/lỗi.
     * @param temperature Tham số độ ngẫu nhiên của model.
     * @return Text trả về từ AI.
     */
    fun requestText(
        prompt: PromptService.PromptSpec,
        serviceName: String,
        temperature: Double
    ): String {
        val candidateModels = buildCandidateModels()
        var lastError: ApiException? = null

        for ((index, model) in candidateModels.withIndex()) {
            try {
                return requestTextWithModel(
                    prompt = prompt,
                    serviceName = serviceName,
                    temperature = temperature,
                    model = model
                )
            } catch (ex: ApiException) {
                lastError = ex
                val canTryNextModel = index < candidateModels.lastIndex
                val isRateLimited = ex.statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value()
                if (isRateLimited && canTryNextModel) {
                    logger.warn("{} model '{}' is rate limited, trying fallback model", serviceName, model)
                    continue
                }

                throw ex
            }
        }

        throw lastError ?: ApiException(
            HttpStatus.BAD_GATEWAY,
            "$serviceName request failed"
        )
    }

    /**
     * Gửi request tới model cụ thể và parse nội dung phản hồi.
     *
     * Behavior của method:
     * - Tạo HTTP POST tới endpoint completions.
     * - Kiểm tra mã trạng thái HTTP.
     * - Trích text từ `choices[].message.content`.
     * - Ném lỗi nếu response không hợp lệ.
     *
     * @param prompt Prompt cần gửi.
     * @param serviceName Tên service để gắn vào lỗi.
     * @param temperature Độ ngẫu nhiên.
     * @param model Tên model cụ thể.
     * @return Text AI trả về.
     */
    private fun requestTextWithModel(
        prompt: PromptService.PromptSpec,
        serviceName: String,
        temperature: Double,
        model: String
    ): String {
        val request = HttpRequest.newBuilder(buildCompletionsUri())
            .timeout(resolveRequestTimeout())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${requireApiKey()}")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    buildRequestBody(prompt, temperature, model),
                    StandardCharsets.UTF_8
                )
            )
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            throw ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "$serviceName request failed: ${response.statusCode()}"
            )
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw ApiException(HttpStatus.BAD_GATEWAY, "$serviceName request failed: ${response.statusCode()}")
        }

        val root = objectMapper.readTree(response.body())
        val result = root.path("choices")
            .asSequence()
            .map { extractMessageText(it.path("message").path("content")) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trim()

        if (result.isEmpty()) {
            throw ApiException(
                HttpStatus.BAD_GATEWAY,
                "$serviceName returned an invalid response",
            )
        }

        return result
    }

    /**
     * Tạo danh sách model ứng viên để thử gọi AI.
     *
     * Behavior của method:
     * - Luôn ưu tiên model chính.
     * - Chỉ thêm fallback nếu khác model chính và không rỗng.
     *
     * @return Danh sách model theo thứ tự thử.
     */
    private fun buildCandidateModels(): List<String> {
        val primary = requireModel()
        val fallback = llmFallbackModel.trim()
        if (fallback.isEmpty() || fallback == primary) {
            return listOf(primary)
        }

        return listOf(primary, fallback)
    }

    /**
     * Tạo URI endpoint completions từ base URL cấu hình.
     *
     * @return URI tới endpoint chat/completions.
     */
    private fun buildCompletionsUri(): URI {
        val baseUrl = llmBaseUrl.trim()
        if (baseUrl.isEmpty()) {
            throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Message AI service is not configured")
        }

        val separator = if (baseUrl.endsWith("/")) "" else "/"
        return URI.create(baseUrl + separator + "chat/completions")
    }

    /**
     * Tạo JSON body cho request completions.
     *
     * Body gồm model, temperature, system prompt và user prompt.
     *
     * @param prompt Prompt cần gửi.
     * @param temperature Độ ngẫu nhiên.
     * @param model Tên model.
     * @return JSON body dạng chuỗi.
     */
    private fun buildRequestBody(
        prompt: PromptService.PromptSpec,
        temperature: Double,
        model: String
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", model)
        root.put("temperature", temperature)

        val messages = root.putArray("messages")
        messages.addObject()
            .put("role", "developer")
            .put("content", prompt.systemPrompt)
        messages.addObject()
            .put("role", "user")
            .put("content", prompt.userPrompt)

        return objectMapper.writeValueAsString(root)
    }

    /**
     * Kiểm tra và lấy API key hợp lệ.
     *
     * @return API key đã được trim.
     */
    private fun requireApiKey(): String {
        val apiKey = llmApiKey.trim()
        if (apiKey.isEmpty()) {
            throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "LLM_API_KEY is not configured")
        }

        return apiKey
    }

    /**
     * Kiểm tra và lấy model chính hợp lệ.
     *
     * @return Tên model đã được trim.
     */
    private fun requireModel(): String {
        val model = llmModel.trim()
        if (model.isEmpty()) {
            throw ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Message AI model is not configured"
            )
        }

        return model
    }

    /**
     * Tính timeout cho request AI.
     *
     * @return Timeout tối thiểu 10 giây.
     */
    private fun resolveRequestTimeout(): Duration {
        return Duration.ofSeconds(maxOf(llmRequestTimeoutSeconds, 10).toLong())
    }

    /**
     * Trích phần text từ node content của phản hồi AI.
     *
     * Behavior của method:
     * - Hỗ trợ content dạng string trực tiếp.
     * - Hỗ trợ content dạng mảng các đoạn text.
     * - Bỏ qua các segment không có text.
     *
     * @param contentNode Node JSON chứa nội dung trả về.
     * @return Chuỗi text đã ghép.
     */
    private fun extractMessageText(contentNode: JsonNode?): String {
        if (contentNode == null || contentNode.isNull) {
            return ""
        }

        if (contentNode.isTextual) {
            return contentNode.asText("")
        }

        if (!contentNode.isArray) {
            return ""
        }

        val text = StringBuilder()
        for (segment in contentNode) {
            if (segment == null || segment.isNull) {
                continue
            }

            if (segment.isTextual) {
                text.append(segment.asText(""))
                continue
            }

            if (segment.path("type").asText("").equals("text", ignoreCase = true)) {
                text.append(segment.path("text").asText(""))
            }
        }

        return text.toString()
    }

}
