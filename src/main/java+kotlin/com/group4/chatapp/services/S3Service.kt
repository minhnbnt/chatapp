package com.group4.chatapp.services

import com.group4.chatapp.exceptions.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Service thao tác với object storage tương thích S3.
 *
 * Service này đảm nhận việc đảm bảo bucket tồn tại, upload file, tạo public URL
 * và phân loại kết quả upload theo success/failure.
 */
@Service
class S3Service(
    private val s3Client: S3Client,
    private val fileTypeService: FileTypeService
) {

    /**
     * Kết quả upload file lên storage.
     *
     * Một file hoặc upload thành công với metadata đầy đủ, hoặc thất bại với
     * thông điệp lỗi để caller xử lý tiếp.
     */
    sealed interface UploadResult {
        val fileName: String

        /**
         * Kết quả upload thành công.
         *
         * Lưu URL an toàn, resource type và format để service khác tạo attachment.
         */
        data class Success(
            override val fileName: String,
            val secureUrl: String,
            val resourceType: String,
            val format: String
        ) : UploadResult

        /**
         * Kết quả upload thất bại.
         *
         * Chứa tên file và lý do lỗi để caller trả về thông điệp phù hợp.
         */
        data class Failure(
            override val fileName: String,
            val message: String
        ) : UploadResult
    }

    private val bucketReady = AtomicBoolean(false)

    @Value("\${s3.public-url}")
    private lateinit var s3PublicUrl: String

    @Value("\${s3.bucket-name}")
    private lateinit var bucketName: String

    /**
     * Upload avatar lên S3 và trả về URL công khai của file.
     *
     * Behavior của method:
     * - Từ chối file rỗng.
     * - Tạo key nằm trong thư mục `avatars`.
     * - Upload file lên storage và trả về public URL.
     * - Nếu upload lỗi, ném `ApiException` với thông điệp phù hợp.
     *
     * @param file File avatar cần upload.
     * @return URL công khai của avatar.
     */
    fun uploadAvatar(file: MultipartFile): String {
        if (file.isEmpty) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Uploaded file is empty")
        }

        return runCatching {
            val key = generateKey(folder = "avatars", filename = file.originalFilename)
            uploadToS3(file, key)
            buildUrl(key)
        }.getOrElse { ex ->
            throw ApiException(HttpStatus.BAD_REQUEST, ex.message ?: "Upload avatar failed")
        }
    }

    /**
     * Upload nhiều file và trả về kết quả chi tiết cho từng file.
     *
     * Behavior của method:
     * - Nếu danh sách rỗng, trả về danh sách rỗng.
     * - Với từng file, ghi nhận thành công hoặc thất bại riêng biệt.
     * - Gắn metadata resource type và format cho file upload thành công.
     *
     * @param files Danh sách file cần upload.
     * @return Danh sách kết quả upload theo từng file.
     */
    fun uploadMany(files: List<MultipartFile>): List<UploadResult> {
        if (files.isEmpty()) {
            return emptyList()
        }

        return files.mapIndexed { index, file ->

            val fallbackName = "file_$index"
            val originalName = file.originalFilename?.takeIf { it.isNotBlank() } ?: fallbackName

            if (file.isEmpty) {
                return@mapIndexed UploadResult.Failure(originalName, "Uploaded file is empty")
            }

            runCatching {
                val resourceType = fileTypeService.getMimeType(file.contentType)
                val format = fileTypeService.getFileExtension(file.originalFilename)
                val key = generateKey(folder = resourceType, filename = file.originalFilename)

                uploadToS3(file, key)

                UploadResult.Success(
                    fileName = originalName,
                    secureUrl = buildUrl(key),
                    resourceType = resourceType,
                    format = format
                )
            }.getOrElse { ex ->
                UploadResult.Failure(
                    fileName = originalName,
                    message = ex.message ?: "Unknown upload error"
                )
            }
        }
    }

    /**
     * Tạo key lưu file trên storage.
     *
     * Behavior của method:
     * - Làm sạch tên file đầu vào.
     * - Thêm UUID và timestamp để tránh trùng key.
     *
     * @param folder Thư mục logic để lưu file.
     * @param filename Tên file gốc.
     * @return Object key an toàn để lưu trên S3.
     */
    private fun generateKey(folder: String, filename: String?): String {
        val sanitizedFilename = sanitizeFilename(filename)
        val timestamp = System.currentTimeMillis()
        return "$folder/${UUID.randomUUID()}_${timestamp}_$sanitizedFilename"
    }

    /**
     * Làm sạch tên file để tránh ký tự nguy hiểm trong object key.
     *
     * Behavior của method:
     * - Nếu tên rỗng, trả về `file`.
     * - Loại bỏ khoảng trắng và các chuỗi path traversal cơ bản.
     *
     * @param filename Tên file gốc.
     * @return Tên file đã được chuẩn hóa.
     */
    private fun sanitizeFilename(filename: String?): String {
        if (!StringUtils.hasText(filename)) {
            return "file"
        }

        return filename!!
            .trim()
            .replace(" ", "_")
            .replace("..", "_")
            .replace("/", "_")
            .replace("\\", "_")
    }

    /**
     * Upload file lên bucket S3.
     *
     * Behavior của method:
     * - Đảm bảo bucket tồn tại trước khi upload.
     * - Gửi file với content type phù hợp.
     *
     * @param file File cần upload.
     * @param key Object key sẽ dùng để lưu file.
     */
    private fun uploadToS3(file: MultipartFile, key: String) {
        ensureBucketExists()

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(file.contentType ?: "application/octet-stream")
            .build()

        file.inputStream.use { stream ->
            s3Client.putObject(request, RequestBody.fromInputStream(stream, file.size))
        }
    }

    /**
     * Đảm bảo bucket đã tồn tại và có policy public read.
     *
     * Behavior của method:
     * - Kiểm tra bucket hiện có.
     * - Nếu chưa tồn tại thì tạo mới.
     * - Thiết lập bucket policy cho phép đọc công khai object.
     * - Chạy đồng bộ để tránh tạo bucket nhiều lần cùng lúc.
     */
    @Synchronized
    private fun ensureBucketExists() {
        if (bucketReady.get()) {
            return
        }

        try {
            s3Client.headBucket(
                HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build()
            )
        } catch (_: NoSuchBucketException) {
            createBucket()
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404 || ex.statusCode() == 301) {
                createBucket()
            } else {
                throw ex
            }
        }

        ensurePublicReadPolicy()

        bucketReady.set(true)
    }

    /**
     * Tạo bucket S3 nếu bucket chưa tồn tại.
     */
    private fun createBucket() {
        s3Client.createBucket(
            CreateBucketRequest.builder()
                .bucket(bucketName)
                .build()
        )
    }

    /**
     * Gán policy public read cho bucket.
     *
     * Policy này cho phép đọc object từ bên ngoài thông qua URL công khai.
     */
    private fun ensurePublicReadPolicy() {
        val policy = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Sid": "PublicReadGetObject",
                  "Effect": "Allow",
                  "Principal": "*",
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::$bucketName/*"]
                }
              ]
            }
        """.trimIndent()

        s3Client.putBucketPolicy(
            PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(policy)
                .build()
        )
    }

    /**
     * Tạo public URL cho object đã upload.
     *
     * @param key Object key trên bucket.
     * @return URL truy cập công khai của file.
     */
    private fun buildUrl(key: String): String {
        val baseUrl = s3PublicUrl.trimEnd('/')
        return "$baseUrl/$bucketName/$key"
    }
}