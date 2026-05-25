package com.group4.chatapp.configs;

import org.msgpack.jackson.dataformat.MessagePackMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Cấu hình hỗ trợ định dạng MessagePack trong HTTP response/request.
 *
 * Class này đăng ký message converter để Spring có thể đọc và ghi dữ liệu
 * theo kiểu MessagePack bên cạnh JSON mặc định.
 */
@Configuration
public class MsgpackConfig implements WebMvcConfigurer {

    /**
     * Tạo converter cho MessagePack.
     *
     * Behavior của method:
     * - Dùng `MessagePackMapper` để serialize/deserialize dữ liệu nhị phân.
     * - Hỗ trợ các media type `application/msgpack` và `application/x-msgpack`.
     * - Giữ hành vi JSON/Jackson tương thích với converter hiện có của Spring.
     *
     * @return HTTP message converter có thể xử lý payload MessagePack.
     */
    @Bean
    public HttpMessageConverter<?> msgpackMessageConverter() {

        var objectMapper = new MessagePackMapper();
        objectMapper.handleBigIntegerAndBigDecimalAsString();

        var supportedMediaTypes = List.of(
            new MediaType("application", "msgpack"),
            new MediaType("application", "x-msgpack")
        );

        var messageConverter = new MappingJackson2HttpMessageConverter();

        messageConverter.setSupportedMediaTypes(supportedMediaTypes);
        messageConverter.setObjectMapper(objectMapper);

        return messageConverter;
    }

    /**
     * Thiết lập media type mặc định của ứng dụng.
     *
     * Nếu client không yêu cầu kiểu dữ liệu cụ thể, ứng dụng sẽ ưu tiên JSON.
     *
     * @param configurer Cấu hình content negotiation của Spring MVC.
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}