package com.group4.chatapp.configs

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

/**
 * Cấu hình client kết nối tới dịch vụ S3 tương thích.
 *
 * Class này tạo `S3Client` từ endpoint, access key và secret key lấy từ cấu hình
 * để service có thể upload và truy xuất file qua object storage.
 */
@Configuration
class S3Config {

    @Value("\${s3.endpoint}")
    private lateinit var endpoint: String

    @Value("\${s3.access-key}")
    private lateinit var accessKey: String

    @Value("\${s3.secret-key}")
    private lateinit var secretKey: String

    /**
     * Tạo S3 client dùng chung cho các service xử lý file.
     *
     * Behavior của method:
     * - Trỏ client đến endpoint cấu hình thay vì AWS mặc định.
     * - Dùng region cố định `US_EAST_1` để khởi tạo client.
     * - Dùng credentials tĩnh từ cấu hình ứng dụng.
     * - Bật `forcePathStyle(true)` để hỗ trợ các S3-compatible storage.
     *
     * @return `S3Client` đã được cấu hình sẵn.
     */
    @Bean
    fun s3Client() = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    accessKey,
                    secretKey
                )
            )
        )
        .forcePathStyle(true)
        .build()
}