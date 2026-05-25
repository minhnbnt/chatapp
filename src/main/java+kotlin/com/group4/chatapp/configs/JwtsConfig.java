package com.group4.chatapp.configs;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Cấu hình tạo encoder và decoder cho JWT dựa trên secret đối xứng.
 *
 * Class này dùng cùng một secret để ký và xác thực JWT, đảm bảo token được
 * phát hành và kiểm tra theo cùng một chuẩn bảo mật trong ứng dụng.
 */
@Configuration
public class JwtsConfig {

    @Value("${jwts.secret}")
    private String jwtSecret;

    /**
     * Tạo khóa bí mật dùng để ký và xác thực JWT.
     *
     * Behavior của method:
     * - Chuyển secret cấu hình sang bytes UTF-8.
     * - Từ chối secret quá ngắn vì không đủ an toàn cho HMAC-SHA256.
     * - Tạo `SecretKey` để các bean JWT encoder/decoder dùng chung.
     *
     * @return Khóa bí mật dùng cho thuật toán HMAC-SHA256.
     * @throws IllegalArgumentException nếu secret ngắn hơn 32 bytes.
     */
    private SecretKey secretKey() {

        var bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("jwts.secret must be at least 32 bytes");
        }

        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Tạo bean dùng để giải mã và xác thực JWT từ secret cấu hình.
     *
     * @return `JwtDecoder` hoạt động với khóa đối xứng của ứng dụng.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).build();
    }

    /**
     * Tạo bean dùng để mã hóa và ký JWT bằng cùng secret với decoder.
     *
     * @return `JwtEncoder` dùng để phát hành token trong ứng dụng.
     */
    @Bean
    public JwtEncoder jwtEncoder() {

        var jwk = new OctetSequenceKey.Builder(secretKey().getEncoded())
            .build();

        var jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));

        return new NimbusJwtEncoder(jwkSource);
    }
}
