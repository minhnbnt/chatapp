package com.group4.chatapp.services

import com.group4.chatapp.dtos.messages.MessageSendDto
import com.group4.chatapp.exceptions.ApiException
import com.group4.chatapp.models.Attachment
import com.group4.chatapp.repositories.AttachmentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Service xử lý vòng đời tệp đính kèm của tin nhắn và avatar.
 *
 * Service này chịu trách nhiệm kiểm tra kiểu file, upload lên storage,
 * và lưu metadata attachment vào database.
 */
@Service
class AttachmentService(
    private val fileTypeService: FileTypeService,
    private val s3Service: S3Service,
    private val attachmentRepository: AttachmentRepository
) {

    /**
     * Lấy attachment theo id hoặc báo lỗi nếu không tồn tại.
     *
     * Behavior của method:
     * - Tìm attachment trong repository.
     * - Nếu không có, ném `ApiException` với mã 404.
     *
     * @param id ID của attachment cần tra cứu.
     * @return Attachment tương ứng.
     */
    fun getAttachmentOrThrow(id: Long): Attachment =
        attachmentRepository.findById(id).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "Attachment not found")
        }

    /**
     * Upload các file đính kèm của một tin nhắn và lưu metadata.
     *
     * Behavior của method:
     * - Nếu request không có file, trả về danh sách rỗng.
     * - Upload toàn bộ file lên S3/object storage.
     * - Nếu tất cả đều thất bại, ném lỗi với thông điệp lỗi đầu tiên.
     * - Với các upload thành công, xác định loại file và lưu attachment.
     *
     * @param dto DTO chứa các file đính kèm của tin nhắn.
     * @return Danh sách attachment đã lưu vào database.
     */
    fun getAttachments(dto: MessageSendDto): List<Attachment> {
        val multipartFiles = dto.attachmentsOrEmpty()
        if (multipartFiles.isEmpty()) {
            return emptyList()
        }

        val uploadedResults = s3Service.uploadMany(multipartFiles)
        if (uploadedResults.isEmpty()) {
            return emptyList()
        }

        val hasSuccess = uploadedResults.any { it is S3Service.UploadResult.Success }
        if (!hasSuccess) {

            val firstError = uploadedResults
                .filterIsInstance<S3Service.UploadResult.Failure>()
                .firstOrNull()
                ?.message
                ?: "Upload failed"

            throw ApiException(HttpStatus.BAD_REQUEST, firstError)
        }

        return uploadedResults
            .asSequence()
            .filterIsInstance<S3Service.UploadResult.Success>()
            .map { result ->
                val attachmentType = fileTypeService.checkTypeInFileType(
                    result.resourceType,
                    result.format
                )

                Attachment.of(result.secureUrl, attachmentType)
            }
            .map(attachmentRepository::save)
            .toList()
    }

    /**
     * Upload avatar của người dùng và lưu attachment tương ứng.
     *
     * Behavior của method:
     * - Từ chối file rỗng.
     * - Kiểm tra MIME type và extension để đảm bảo file là ảnh.
     * - Upload file lên storage, tạo attachment loại IMAGE và lưu vào DB.
     *
     * @param avatarFile File avatar được client gửi lên.
     * @return Attachment của avatar đã lưu.
     */
    fun uploadAvatar(avatarFile: MultipartFile): Attachment {
        if (avatarFile.isEmpty) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Avatar must not be empty")
        }

        val resourceType = fileTypeService.getMimeType(avatarFile.contentType)
        val format = fileTypeService.getFileExtension(avatarFile.originalFilename)
        val fileType = fileTypeService.checkTypeInFileType(resourceType, format)

        if (fileType != Attachment.FileType.IMAGE) {
            throw ApiException(HttpStatus.BAD_REQUEST, "Avatar must be an image")
        }

        val uploadedUrl = s3Service.uploadAvatar(avatarFile)
        val attachment = Attachment.of(uploadedUrl, Attachment.FileType.IMAGE)

        return attachmentRepository.save(attachment)
    }
}