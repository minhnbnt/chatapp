package com.group4.chatapp.configs;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;

/**
 * Cấu hình khai báo các security scheme cho tài liệu OpenAPI.
 *
 * Class này chỉ phục vụ mô tả cơ chế xác thực trong Swagger/OpenAPI,
 * không tác động trực tiếp đến logic bảo mật runtime của ứng dụng.
 */
@SecuritySchemes({
    @SecurityScheme(

        scheme = "basic",
        name = "basicAuth",

        type = SecuritySchemeType.HTTP
    ),
    @SecurityScheme(

        scheme = "bearer",
        name = "bearerAuth",
        bearerFormat = "JWT",

        type = SecuritySchemeType.HTTP
    )
})
public class OpenApiConfig {}
